#ifndef SOTAK_JITTER_BUFFER_H
#define SOTAK_JITTER_BUFFER_H

#include <cstdint>
#include <mutex>
#include <vector>

/**
 * A small re-ordering buffer for real-time voice frames.
 *
 * UDP packets may arrive out of order, late, or not at all. Playout always
 * advances one frame at a time at the audio clock rate; if the expected frame
 * is missing we signal a loss so the decoder can do packet-loss concealment
 * (or plain silence) instead of stalling. That is what keeps the audio smooth.
 */
class JitterBuffer {
public:
    enum class Result { Packet, Loss, Empty };

    explicit JitterBuffer(size_t capacity = 256);

    void reset();
    void push(uint32_t seq, const uint8_t* data, size_t len);
    Result pop(std::vector<uint8_t>& out);

private:
    struct Slot {
        bool present = false;
        uint32_t seq = 0;
        std::vector<uint8_t> data;
    };

    std::vector<Slot> slots_;
    size_t capacity_;
    uint32_t head_ = 0;
    bool started_ = false;
    size_t count_ = 0;
    std::mutex mutex_;
};

#endif  // SOTAK_JITTER_BUFFER_H
