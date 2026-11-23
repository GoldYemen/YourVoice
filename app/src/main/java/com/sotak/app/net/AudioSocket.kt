package com.sotak.app.net

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Real-time audio transport over the Wi-Fi Direct link.
 *
 * A short TCP control channel performs the handshake and lets the host learn the
 * client's UDP port; the live audio itself flows over UDP so a single late packet
 * never blocks the ones behind it (no head-of-line blocking, no stutter).
 *
 * The Wi-Fi Direct group owner always acts as the host/server.
 */
class AudioSocket(
    private val isHost: Boolean,
    private val hostAddress: String?,
) {
    companion object {
        private const val TAG = "SotakSocket"
        const val CONTROL_PORT = 8988
        const val AUDIO_PORT = 8989
        private const val MAX_DATAGRAM = 2048
    }

    @Volatile
    private var running = false

    private var controlSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var datagramSocket: DatagramSocket? = null

    private var peerAddress: InetAddress? = null
    private var peerAudioPort = 0

    private var onPacket: ((ByteArray) -> Unit)? = null
    private var onReady: (() -> Unit)? = null

    fun setListener(onReady: () -> Unit, onPacket: (ByteArray) -> Unit) {
        this.onReady = onReady
        this.onPacket = onPacket
    }

    fun open() {
        running = true
        thread(name = "sotak-control") { if (isHost) runAsHost() else runAsClient() }
    }

    private fun runAsHost() {
        try {
            val tcp = ServerSocket(CONTROL_PORT).apply { soTimeout = 20_000 }
            serverSocket = tcp
            val socket = tcp.accept()
            controlSocket = socket

            // The client tells us which local UDP port it is listening on.
            val reader = socket.getInputStream().bufferedReader()
            val line = reader.readLine() ?: return
            val port = line.trim().removePrefix("PORT").trim().toIntOrNull() ?: AUDIO_PORT

            peerAddress = socket.inetAddress
            peerAudioPort = port

            datagramSocket = DatagramSocket(AUDIO_PORT)
            socket.getOutputStream().write("OK\n".toByteArray())
            socket.getOutputStream().flush()

            startReceiveLoop()
            onReady?.invoke()
        } catch (t: Throwable) {
            Log.e(TAG, "host handshake failed", t)
        }
    }

    private fun runAsClient() {
        try {
            val host = InetAddress.getByName(hostAddress)
            val socket = Socket(host, CONTROL_PORT).apply { soTimeout = 15_000 }
            controlSocket = socket

            val udp = DatagramSocket()
            udp.connect(host, AUDIO_PORT) // only accept datagrams from the host
            datagramSocket = udp

            socket.getOutputStream().write("PORT ${udp.localPort}\n".toByteArray())
            socket.getOutputStream().flush()

            peerAddress = host
            peerAudioPort = AUDIO_PORT

            startReceiveLoop()
            // Wait for the host to confirm it is listening. A timeout is ignored
            // so the call still starts instead of hanging forever.
            runCatching { socket.getInputStream().bufferedReader().readLine() }
            onReady?.invoke()
        } catch (t: Throwable) {
            Log.e(TAG, "client handshake failed", t)
        }
    }

    private fun startReceiveLoop() {
        val udp = datagramSocket ?: return
        thread(name = "sotak-udp-rx") {
            val buffer = ByteArray(MAX_DATAGRAM)
            while (running) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    onPacket?.invoke(packet.data.copyOf(packet.length))
                } catch (t: Throwable) {
                    if (running) Log.e(TAG, "udp receive failed", t)
                }
            }
        }
    }

    fun send(data: ByteArray) {
        val udp = datagramSocket ?: return
        val address = peerAddress ?: return
        if (peerAudioPort == 0) return
        try {
            udp.send(DatagramPacket(data, data.size, address, peerAudioPort))
        } catch (t: Throwable) {
            if (running) Log.e(TAG, "udp send failed", t)
        }
    }

    fun close() {
        running = false
        runCatching { controlSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { datagramSocket?.close() }
        controlSocket = null
        serverSocket = null
        datagramSocket = null
    }
}
