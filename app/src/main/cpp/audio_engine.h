#ifndef SOTAK_AUDIO_ENGINE_H
#define SOTAK_AUDIO_ENGINE_H

#include <cstdint>
#include <mutex>
#include <vector>

#include <opus.h>

#include "jitter_buffer.h"

/**
 * Owns the whole native voice pipeline for one call:
 *   PCM -> Opus encode  ...  Opus decode -> PCM, with a jitter buffer in between.
 *
 * Everything runs without JVM interaction beyond the raw frame hand-off, so the
 * hot path stays allocation-light and cheap on battery.
 */
class AudioEngine {
public:
    AudioEngine(int sampleRate, int channels, int bitrate);
    ~AudioEngine();

    AudioEngine(const AudioEngine&) = delete;
    AudioEngine& operator=(const AudioEngine&) = delete;

    int frameSize() const { return frameSize_; }
    int sampleRate() const { return sampleRate_; }
    int channels() const { return channels_; }

    // Encode one PCM frame (exactly frameSize() samples per channel).
    std::vector<uint8_t> encode(const int16_t* pcm, int samples);

    // Decode one Opus packet (or run concealment when data == nullptr).
    std::vector<int16_t> decode(const uint8_t* data, int len, bool fec);

    // Network hand-off for the receive path.
    void pushPacket(uint32_t seq, const uint8_t* data, size_t len);
    bool popFrame(std::vector<int16_t>& out);

    void reset();

private:
    int sampleRate_;
    int channels_;
    int frameSize_;
    OpusEncoder* encoder_ = nullptr;
    OpusDecoder* decoder_ = nullptr;
    JitterBuffer jitter_;
    std::mutex mutex_;
};

#endif  // SOTAK_AUDIO_ENGINE_H
