#include "jitter_buffer.h"

JitterBuffer::JitterBuffer(size_t capacity) : capacity_(capacity) {
    if (capacity_ < 8) capacity_ = 8;
    slots_.resize(capacity_);
}

void JitterBuffer::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    for (auto& s : slots_) {
        s.present = false;
        s.data.clear();
    }
    head_ = 0;
    started_ = false;
    count_ = 0;
}

void JitterBuffer::push(uint32_t seq, const uint8_t* data, size_t len) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!started_) {
        head_ = seq;
        started_ = true;
    }

    // Already played out -> drop as too old.
    if (static_cast<int32_t>(seq - head_) < 0) {
        return;
    }
    // Too far ahead -> out of the window, drop.
    if (seq - head_ >= capacity_) {
        return;
    }

    Slot& slot = slots_[seq % capacity_];
    if (!slot.present) {
        ++count_;
    }
    slot.present = true;
    slot.seq = seq;
    slot.data.assign(data, data + len);
}

JitterBuffer::Result JitterBuffer::pop(std::vector<uint8_t>& out) {
    std::lock_guard<std::mutex> lock(mutex_);

    if (!started_ || count_ == 0) {
        return Result::Empty;
    }

    Slot& headSlot = slots_[head_ % capacity_];
    if (headSlot.present && headSlot.seq == head_) {
        out = std::move(headSlot.data);
        headSlot.present = false;
        headSlot.data.clear();
        --count_;
        ++head_;
        return Result::Packet;
    }

    // The expected frame is missing. Look ahead for the next real packet.
    bool found = false;
    uint32_t next = 0;
    for (uint32_t d = 1; d < capacity_; ++d) {
        uint32_t candidate = head_ + d;
        Slot& s = slots_[candidate % capacity_];
        if (s.present && s.seq == candidate) {
            found = true;
            next = candidate;
            break;
        }
    }

    if (!found) {
        // Nothing usable ahead -> treat as underrun, stay idle.
        return Result::Empty;
    }

    uint32_t gap = next - head_;
    if (gap == 1) {
        // A single lost frame: let the decoder conceal it.
        ++head_;
        return Result::Loss;
    }

    // A real silence gap (peer paused / network stall): skip it silently so we
    // do not synthesise a burst of concealment frames.
    head_ = next;
    return Result::Empty;
}
