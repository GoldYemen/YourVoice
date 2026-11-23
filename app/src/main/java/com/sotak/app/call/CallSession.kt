package com.sotak.app.call

import android.util.Log
import com.sotak.app.audio.AudioCapture
import com.sotak.app.audio.AudioEngine
import com.sotak.app.audio.AudioPlayback
import com.sotak.app.net.AudioSocket
import com.sotak.app.net.WirePacket
import java.util.concurrent.atomic.AtomicInteger

/**
 * A single live voice session.
 *
 * Wires together the microphone, the native Opus engine, the UDP transport and
 * the speaker. Capture, playout and networking each run on their own thread and
 * meet only through cheap hand-offs, so the audio clock is never held up.
 */
class CallSession(
    private val isHost: Boolean,
    private val hostAddress: String?,
    private val sampleRate: Int = 48_000,
) {
    private companion object {
        const val TAG = "SotakCall"
    }

    private val engine = AudioEngine(sampleRate = sampleRate, channels = 1, bitrate = 32_000)
    private val socket = AudioSocket(isHost = isHost, hostAddress = hostAddress)
    private val sequence = AtomicInteger(0)

    @Volatile
    private var muted = false

    private var capture: AudioCapture? = null
    private var playback: AudioPlayback? = null

    fun setMuted(value: Boolean) {
        muted = value
    }

    fun start(onReady: () -> Unit, onError: (String) -> Unit) {
        val frameSize = engine.frameSize
        if (frameSize <= 0) {
            onError("تعذّر تهيئة محرّك الصوت")
            return
        }

        socket.setListener(
            onReady = {
                Log.d(TAG, "link ready")
                onReady()
            },
            onPacket = { data ->
                if (data.size > WirePacket.HEADER_SIZE) {
                    engine.pushPacket(WirePacket.sequenceOf(data), WirePacket.payloadOf(data))
                }
            },
        )
        socket.open()

        capture = AudioCapture(sampleRate, frameSize) { frame ->
            // While muted we keep the exact same cadence but send silence, so the
            // far end simply hears quiet instead of a broken stream.
            val toSend = if (muted) ShortArray(frame.size) else frame
            val payload = engine.encode(toSend) ?: return@AudioCapture
            socket.send(WirePacket.pack(sequence.getAndIncrement(), payload))
        }.also { it.start() }

        playback = AudioPlayback(sampleRate) { engine.popFrame() }.also { it.start() }
    }

    fun stop() {
        capture?.stop()
        capture = null
        playback?.stop()
        playback = null
        socket.close()
        engine.close()
    }
}
