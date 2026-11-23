#include "audio_engine.h"

#include <algorithm>
#include <android/log.h>

#define LOG_TAG "SotakAudio"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
// 20 ms frames: the standard sweet spot for low latency and good compression.
constexpr int kFrameMs = 20;
constexpr int kMaxPacketBytes = 4000;
}  // namespace

AudioEngine::AudioEngine(int sampleRate, int channels, int bitrate)
    : sampleRate_(sampleRate), channels_(channels) {
    frameSize_ = sampleRate_ * kFrameMs / 1000;

    int err = OPUS_OK;
    encoder_ = opus_encoder_create(sampleRate_, channels_, OPUS_APPLICATION_VOIP, &err);
    if (encoder_ != nullptr && err == OPUS_OK) {
        opus_encoder_ctl(encoder_, OPUS_SET_BITRATE(bitrate));
        opus_encoder_ctl(encoder_, OPUS_SET_INBAND_FEC(1));
        opus_encoder_ctl(encoder_, OPUS_SET_PACKET_LOSS_PERC(10));
        opus_encoder_ctl(encoder_, OPUS_SET_COMPLEXITY(5));
        opus_encoder_ctl(encoder_, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
        opus_encoder_ctl(encoder_, OPUS_SET_DTX(0));
    } else {
        LOGE("Opus encoder init failed: %d", err);
    }

    decoder_ = opus_decoder_create(sampleRate_, channels_, &err);
    if (decoder_ == nullptr || err != OPUS_OK) {
        LOGE("Opus decoder init failed: %d", err);
    }
}

AudioEngine::~AudioEngine() {
    if (encoder_ != nullptr) opus_encoder_destroy(encoder_);
    if (decoder_ != nullptr) opus_decoder_destroy(decoder_);
}

std::vector<uint8_t> AudioEngine::encode(const int16_t* pcm, int samples) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (encoder_ == nullptr || pcm == nullptr || samples != frameSize_) {
        return {};
    }

    std::vector<uint8_t> out(kMaxPacketBytes);
    const int written = opus_encode(encoder_, pcm, frameSize_, out.data(),
                                    static_cast<opus_int32>(out.size()));
    if (written < 0) {
        LOGE("opus_encode failed: %d", written);
        return {};
    }
    out.resize(static_cast<size_t>(written));
    return out;
}

std::vector<int16_t> AudioEngine::decode(const uint8_t* data, int len, bool fec) {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<int16_t> out(static_cast<size_t>(frameSize_) * channels_);
    if (decoder_ == nullptr) {
        return out;
    }

    int decoded = opus_decode(decoder_, data, len, out.data(), frameSize_, fec ? 1 : 0);
    if (decoded < 0) {
        // Any corruption falls back to packet-loss concealment.
        decoded = opus_decode(decoder_, nullptr, 0, out.data(), frameSize_, 0);
    }
    if (decoded <= 0) {
        std::fill(out.begin(), out.end(), static_cast<int16_t>(0));
        return out;
    }
    out.resize(static_cast<size_t>(decoded) * channels_);
    return out;
}

void AudioEngine::pushPacket(uint32_t seq, const uint8_t* data, size_t len) {
    jitter_.push(seq, data, len);
}

bool AudioEngine::popFrame(std::vector<int16_t>& out) {
    std::vector<uint8_t> packet;
    const JitterBuffer::Result r = jitter_.pop(packet);
    switch (r) {
        case JitterBuffer::Result::Empty:
            return false;
        case JitterBuffer::Result::Loss:
            out = decode(nullptr, 0, false);
            return true;
        case JitterBuffer::Result::Packet:
            out = decode(packet.data(), static_cast<int>(packet.size()), false);
            return true;
    }
    return false;
}

void AudioEngine::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (encoder_ != nullptr) opus_encoder_ctl(encoder_, OPUS_RESET_STATE);
    if (decoder_ != nullptr) opus_decoder_ctl(decoder_, OPUS_RESET_STATE);
    jitter_.reset();
}
