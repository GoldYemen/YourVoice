package com.sotak.app.audio

import java.io.Closeable

/**
 * Kotlin bridge over the native Opus pipeline (see cpp/audio_engine.cpp).
 *
 * All the heavy work -- Opus encode/decode and the re-ordering jitter buffer --
 * lives in C++ so the audio thread never touches the garbage collector. That is
 * what keeps the call light and the voice free of stutter.
 */
class AudioEngine(
    val sampleRate: Int = 48_000,
    val channels: Int = 1,
    bitrate: Int = 32_000,
) : Closeable {

    private var handle: Long = nativeCreate(sampleRate, channels, bitrate)

    /** Number of PCM samples in one 20 ms frame. */
    val frameSize: Int
        get() = if (handle != 0L) nativeFrameSize(handle) else 0

    fun encode(pcm: ShortArray): ByteArray? =
        if (handle != 0L) nativeEncode(handle, pcm) else null

    fun decode(packet: ByteArray?, fec: Boolean = false): ShortArray? =
        if (handle != 0L) nativeDecode(handle, packet, fec) else null

    fun pushPacket(sequence: Int, packet: ByteArray) {
        if (handle != 0L) nativePushPacket(handle, sequence, packet)
    }

    /** Returns the next playable frame, or null when nothing is buffered yet. */
    fun popFrame(): ShortArray? = if (handle != 0L) nativePopFrame(handle) else null

    fun reset() {
        if (handle != 0L) nativeReset(handle)
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int, bitrate: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeFrameSize(handle: Long): Int
    private external fun nativeEncode(handle: Long, pcm: ShortArray): ByteArray?
    private external fun nativeDecode(handle: Long, packet: ByteArray?, fec: Boolean): ShortArray?
    private external fun nativePushPacket(handle: Long, sequence: Int, packet: ByteArray)
    private external fun nativePopFrame(handle: Long): ShortArray?
    private external fun nativeReset(handle: Long)

    companion object {
        init {
            System.loadLibrary("sotak_audio")
        }
    }
}
