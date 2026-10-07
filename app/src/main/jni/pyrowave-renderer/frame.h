#pragma once
#include <cstddef>
#include <cstdint>

inline uint32_t pyroReadLe32(const uint8_t *p) {
    return uint32_t(p[0]) | (uint32_t(p[1]) << 8) | (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24);
}

// Butterpollo's packet container, after the core has removed the short header and FEC padding.
template <typename Push>
bool pushPyroWaveFrame(const uint8_t *data, size_t length, Push push) {
    if (length < 4) return false;
    const uint32_t count = pyroReadLe32(data);
    if (count == 0 || count > (length - 4) / 12) return false;
    size_t offset = 4;
    uint32_t sequence = UINT32_MAX;
    for (uint32_t i = 0; i < count; ++i) {
        if (length - offset < 4) return false;
        const uint32_t size = pyroReadLe32(data + offset);
        offset += 4;
        if (size < 8 || (size & 3) || size > length - offset) return false;
        // Reject short blocks before the decoder, including duplicated zero-length blocks.
        for (size_t at = 0; at < size;) {
            if (size - at < 8) return false;
            const uint32_t word = pyroReadLe32(data + offset + at);
            const size_t blockSize = word & 0x80000000u ? 8 : ((word >> 16) & 0xfff) * 4;
            if (word == UINT32_MAX || blockSize < 8 || blockSize > size - at) return false;
            const uint32_t blockSequence = (word >> 28) & 7;
            if (sequence != UINT32_MAX && sequence != blockSequence) return false;
            sequence = blockSequence;
            at += blockSize;
        }
        offset += size;
    }
    if (offset != length) return false;

    // Reject the whole container before any packet can change the decoder's sequence state.
    offset = 4;
    for (uint32_t i = 0; i < count; ++i) {
        const uint32_t size = pyroReadLe32(data + offset);
        offset += 4;
        if (!push(data + offset, size)) return false;
        offset += size;
    }
    return true;
}
