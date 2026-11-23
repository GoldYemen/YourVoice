package com.sotak.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread

/**
 * Continuous playback on its own thread.
 *
 * Pulls decoded frames from [onPull] and streams them to a low-latency
 * AudioTrack. When there is nothing buffered it waits briefly instead of busy
 * spinning, so battery drain stays minimal.
 */
class AudioPlayback(
    private val sampleRate: Int,
    private val onPull: () -> ShortArray?,
) {
    private var track: AudioTrack? = null

    @Volatile
    private var running = false

    fun start() {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return

        // ~200 ms of buffer smooths out jitter spikes without adding noticeable delay.
        val bufferSize = maxOf(minBuffer, sampleRate * 2 / 5)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            return
        }
        track = audioTrack
        audioTrack.play()
        running = true

        thread(name = "sotak-playback", priority = Thread.MAX_PRIORITY) {
            while (running) {
                val frame = onPull()
                if (frame != null && frame.isNotEmpty()) {
                    audioTrack.write(frame, 0, frame.size)
                } else {
                    Thread.sleep(5)
                }
            }
        }
    }

    fun stop() {
        running = false
        track?.let { audioTrack ->
            runCatching { audioTrack.stop() }
            audioTrack.release()
        }
        track = null
    }
}
