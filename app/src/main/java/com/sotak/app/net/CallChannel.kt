package com.sotak.app.net

import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The full call signalling + audio transport for one Wi-Fi Direct link.
 *
 * Control runs over TCP so the handshake is reliable; once both sides agree to
 * talk, the voice itself flows over UDP (a late packet must never block the ones
 * behind it).
 *
 * Protocol (line based):
 * ```
 *   caller -> "RING <name>"
 *   callee -> "ACCEPT <name>"  |  "DECLINE"
 *   (only after ACCEPT)
 *   tcp-client -> "PORT <udpPort>"
 *   tcp-server -> "READY"
 * ```
 * Either side may be the TCP server: whoever the Wi-Fi Direct negotiation made
 * the *group owner* listens, the other connects.
 */
class CallChannel(
    private val isHost: Boolean,
    private val hostAddress: String?,
    private val isCaller: Boolean,
    private val localName: String,
) {

    interface Listener {
        /** Callee side: someone is ringing us. */
        fun onIncomingCall(peerName: String)

        /** The other side picked up. */
        fun onAccepted(peerName: String)

        /** The other side refused, or gave up waiting. */
        fun onDeclined()

        /** Audio is ready to flow on both ends. */
        fun onReady()

        /** The link could not be established. */
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "SotakChannel"

        const val CONTROL_PORT = 8988
        const val AUDIO_PORT = 8989

        private const val MAX_DATAGRAM = 2048
        private const val RING = "RING"
        private const val ACCEPT = "ACCEPT"
        private const val DECLINE = "DECLINE"
        private const val CANCEL = "CANCEL"
        private const val PORT = "PORT"
        private const val READY = "READY"

        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val RING_WAIT_MS = 20_000L   // caller: how long to ring before giving up
        private const val RING_ANSWER_MS = 40_000L // callee: how long the phone rings
        private const val FALLBACK_PEER = "جهاز قريب"
    }

    /** Audio packets arriving from the network. Set by [com.sotak.app.call.CallSession]. */
    @Volatile
    var onAudioPacket: ((ByteArray) -> Unit)? = null

    @Volatile
    private var closed = false

    @Volatile
    private var running = false

    @Volatile
    private var peerClosed = false

    private var controlSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var datagramSocket: DatagramSocket? = null

    private var peerAddress: InetAddress? = null
    private var peerAudioPort = 0

    private var writer: BufferedWriter? = null

    private val inbound = LinkedBlockingQueue<String>()
    private val decision = LinkedBlockingQueue<String>()

    fun open(listener: Listener) {
        thread(name = "sotak-channel") { run(listener) }
    }

    /** Callee answered the call. */
    fun accept() {
        decision.offer(ACCEPT)
    }

    /** Callee refused the call. */
    fun decline() {
        decision.offer(DECLINE)
    }

    fun sendAudio(payload: ByteArray) {
        val udp = datagramSocket ?: return
        val address = peerAddress ?: return
        if (peerAudioPort == 0) return
        runCatching { udp.send(DatagramPacket(payload, payload.size, address, peerAudioPort)) }
            .onFailure { if (running) Log.e(TAG, "udp send failed", it) }
    }

    fun close() {
        closed = true
        running = false
        runCatching { writer?.let { writeLine(it, CANCEL) } }
        runCatching { controlSocket?.close() }
        runCatching { serverSocket?.close() }
        runCatching { datagramSocket?.close() }
        controlSocket = null
        serverSocket = null
        datagramSocket = null
    }

    // ---------------------------------------------------------------- internals

    private fun run(listener: Listener) {
        try {
            val socket = openControlSocket()
            controlSocket = socket

            val reader = socket.getInputStream().bufferedReader()
            val out = socket.getOutputStream().bufferedWriter()
            writer = out

            startControlReader(reader)

            if (isCaller) {
                writeLine(out, "$RING $localName")
                val line = awaitLine(RING_WAIT_MS) ?: throw IOException("لا يوجد رد")
                when {
                    line.startsWith(ACCEPT) -> listener.onAccepted(peerNameOf(line, ACCEPT))
                    line.startsWith(DECLINE) -> {
                        listener.onDeclined()
                        return
                    }
                    else -> throw IOException("رد غير متوقع")
                }
            } else {
                val line = awaitLine(RING_WAIT_MS) ?: throw IOException("انقطع الاتصال")
                if (!line.startsWith(RING)) throw IOException("رسالة غير متوقعة")
                val name = peerNameOf(line, RING)
                listener.onIncomingCall(name)

                when (awaitDecision()) {
                    ACCEPT -> {
                        writeLine(out, "$ACCEPT $localName")
                        listener.onAccepted(name)
                    }
                    else -> {
                        runCatching { writeLine(out, DECLINE) }
                        listener.onDeclined()
                        return
                    }
                }
            }

            val udp = negotiateAudio(socket, out)
            datagramSocket = udp
            running = true
            startReceiveLoop(udp)
            listener.onReady()
        } catch (t: Throwable) {
            if (!closed) {
                Log.e(TAG, "channel failed", t)
                listener.onError(t.message ?: "تعذّر إنشاء الاتصال")
            }
        }
    }

    private fun openControlSocket(): Socket {
        return if (isHost) {
            val server = ServerSocket(CONTROL_PORT).apply { soTimeout = CONNECT_TIMEOUT_MS.toInt() }
            serverSocket = server
            server.accept()
        } else {
            Socket().apply {
                connect(InetSocketAddress(hostAddress, CONTROL_PORT), CONNECT_TIMEOUT_MS.toInt())
            }
        }
    }

    private fun startControlReader(reader: BufferedReader) {
        thread(name = "sotak-control-rx") {
            try {
                while (!closed) {
                    val line = reader.readLine() ?: break
                    inbound.offer(line)
                }
            } catch (_: Throwable) {
                // Socket closed underneath us -- treated as a peer hang-up.
            } finally {
                peerClosed = true
            }
        }
    }

    /** Blocks until the callee answers, the caller gives up, or the ring times out. */
    private fun awaitDecision(): String {
        val deadline = System.currentTimeMillis() + RING_ANSWER_MS
        while (System.currentTimeMillis() < deadline) {
            val answer = decision.poll(200, TimeUnit.MILLISECONDS)
            if (answer != null) return answer

            val pending = inbound.poll()
            if (pending != null && pending.startsWith(CANCEL)) return DECLINE
            if (peerClosed) return DECLINE
        }
        return DECLINE
    }

    private fun negotiateAudio(socket: Socket, out: BufferedWriter): DatagramSocket {
        return if (isHost) {
            val line = awaitLine(CONNECT_TIMEOUT_MS) ?: throw IOException("انقطع الاتصال")
            val port = line.removePrefix(PORT).trim().toIntOrNull() ?: AUDIO_PORT
            peerAddress = socket.inetAddress
            peerAudioPort = port
            val udp = DatagramSocket(AUDIO_PORT)
            writeLine(out, READY)
            udp
        } else {
            val address = InetAddress.getByName(hostAddress)
            val udp = DatagramSocket().apply { connect(address, AUDIO_PORT) }
            peerAddress = address
            peerAudioPort = AUDIO_PORT
            writeLine(out, "$PORT ${udp.localPort}")
            awaitLine(CONNECT_TIMEOUT_MS) // "READY" -- best effort
            udp
        }
    }

    private fun startReceiveLoop(udp: DatagramSocket) {
        thread(name = "sotak-udp-rx") {
            val buffer = ByteArray(MAX_DATAGRAM)
            while (running && !closed) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    onAudioPacket?.invoke(packet.data.copyOf(packet.length))
                } catch (t: Throwable) {
                    if (running && !closed) Log.e(TAG, "udp receive failed", t)
                }
            }
        }
    }

    private fun awaitLine(timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val line = inbound.poll(200, TimeUnit.MILLISECONDS)
            if (line != null) return line
            if (peerClosed) return null
            if (System.currentTimeMillis() >= deadline) return null
        }
    }

    private fun peerNameOf(line: String, prefix: String): String =
        line.removePrefix(prefix).trim().ifBlank { FALLBACK_PEER }

    private fun writeLine(out: BufferedWriter, line: String) {
        out.write(line)
        out.write("\n")
        out.flush()
    }
}
