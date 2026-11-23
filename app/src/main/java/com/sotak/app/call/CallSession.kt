package com.sotak.app.call

import com.sotak.app.audio.AudioCapture
import com.sotak.app.audio.AudioEngine
import com.sotak.app.audio.AudioPlayback
import com.sotak.app.net.CallChannel
import com.sotak.app.net.WirePacket
import java.util.concurrent.atomic.AtomicInteger

/**
 * The audio half of a live call.
 *
 * Capture, playout and networking each run on their own thread and meet only
 * through cheap hand-offs, so the audio clock is never held up. The transport
 * itself lives in [CallChannel]; this class only turns sound into packets and
 * back again.
 */
class CallSession(
    private val channel: CallChannel,
    private val sampleRate: Int = 48_000,
) {

    private val engine = AudioEngine(sampleRate = sampleRate, channels = 1, bitrate = 32_000)
    private val sequence = AtomicInteger(0)

    @Volatile
    private var muted = false

    private var capture: AudioCapture? = null
    private var playback: AudioPlayback? = null

    fun setMuted(value: Boolean) {
        muted = value
    }

    fun start() {
        val frameSize = engine.frameSize
        if (frameSize <= 0) return

        channel.onAudioPacket = { data ->
            if (data.size > WirePacket.HEADER_SIZE) {
                engine.pushPacket(WirePacket.sequenceOf(data), WirePacket.payloadOf(data))
            }
        }

        capture = AudioCapture(sampleRate, frameSize) { frame ->
            // While muted we keep the exact same cadence but send silence, so the
            // far end simply hears quiet instead of a broken stream.
            val toSend = if (muted) ShortArray(frame.size) else frame
            val payload = engine.encode(toSend) ?: return@AudioCapture
            channel.sendAudio(WirePacket.pack(sequence.getAndIncrement(), payload))
        }.also { it.start() }

        playback = AudioPlayback(sampleRate) { engine.popFrame() }.also { it.start() }
    }

    fun stop() {
        capture?.stop()
        capture = null
        playback?.stop()
        playback = null
        channel.onAudioPacket = null
        engine.close()
    }
}
