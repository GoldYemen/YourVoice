package com.sotak.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.concurrent.thread

/**
 * Microphone capture on a dedicated high-priority thread.
 *
 * Uses the VOICE_COMMUNICATION source so the platform echo canceller and noise
 * suppressor engage, then explicitly enables them where the device exposes them.
 * Frames are handed to [onFrame] at a steady 20 ms cadence.
 */
class AudioCapture(
    private val sampleRate: Int,
    private val frameSize: Int,
    private val onFrame: (ShortArray) -> Unit,
) {
    private var record: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return

        // A little headroom over the minimum keeps `read` from ever blocking late.
        val bufferSize = maxOf(minBuffer, frameSize * 2 * 4)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }
        record = recorder

        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                runCatching { enabled = true }
            }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.apply {
                runCatching { enabled = true }
            }
        }

        running = true
        recorder.startRecording()

        thread(name = "sotak-capture", priority = Thread.MAX_PRIORITY) {
            val frame = ShortArray(frameSize)
            while (running) {
                var filled = 0
                while (filled < frameSize && running) {
                    val read = recorder.read(frame, filled, frameSize - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (filled == frameSize) {
                    // Hand a private copy to the encoder; the buffer is reused.
                    onFrame(frame.copyOf())
                }
            }
        }
    }

    fun stop() {
        running = false
        echoCanceler?.let { runCatching { it.release() } }
        echoCanceler = null
        noiseSuppressor?.let { runCatching { it.release() } }
        noiseSuppressor = null
        record?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        record = null
    }
}
