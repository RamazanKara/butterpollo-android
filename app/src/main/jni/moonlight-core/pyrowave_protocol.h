#pragma once
#include <string.h>
#include "moonlight-common-c/src/Limelight.h"

#define VIDEO_FORMAT_PYROWAVE 0x010000
#define VIDEO_FORMAT_PYROWAVE_444 0x020000
#define VIDEO_FORMAT_PYROWAVE_MAIN10 0x040000
#define VIDEO_FORMAT_PYROWAVE_MAIN10_444 0x080000
#define VIDEO_FORMAT_MASK_PYROWAVE 0x0F0000
#undef VIDEO_FORMAT_MASK_10BIT
#define VIDEO_FORMAT_MASK_10BIT 0x0CAA00
#undef VIDEO_FORMAT_MASK_YUV444
#define VIDEO_FORMAT_MASK_YUV444 0x0ACC04

static inline int selectPyroWaveFormat(int formats, unsigned int serverFormats, const char* sdp) {
    // Match a complete attribute: a future bitstream revision must use the ordinary codecs.
    if (!strstr(sdp, "PYROWAVE/90000") ||
        (!strstr(sdp, "a=x-ss-pyrowave.bitstream:186f0393\r\n") &&
         !strstr(sdp, "a=x-ss-pyrowave.bitstream:186f0393\n"))) {
        return 0;
    }
    const int client[] = { VIDEO_FORMAT_PYROWAVE_MAIN10_444, VIDEO_FORMAT_PYROWAVE_MAIN10,
                           VIDEO_FORMAT_PYROWAVE_444, VIDEO_FORMAT_PYROWAVE };
    const unsigned int host[] = { 0x04000000, 0x02000000, 0x01000000, 0x00800000 };
    for (unsigned int i = 0; i < sizeof(client) / sizeof(client[0]); i++) {
        if ((formats & client[i]) && (serverFormats & host[i])) {
            return client[i];
        }
    }
    return 0;
}
