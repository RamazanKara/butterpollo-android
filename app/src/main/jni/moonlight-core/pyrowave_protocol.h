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

extern int PyroWaveRecordsSupported;
extern int PyroWaveRecordsEnabled;

// Records shipped with Butterpollo 2.0.0-rc.1 (331b1bfaa1a0).
static inline int supportsPyroWaveRecords(const char* version) {
    if (!version) return 0;
    unsigned int parts[3];
    for (int i = 0; i < 3; i++) {
        const char* start = version;
        unsigned int value = 0;
        while (*version >= '0' && *version <= '9') {
            if (value > 100000) return 0;
            value = value * 10 + (unsigned int)(*version++ - '0');
        }
        if (version == start || (version - start > 1 && *start == '0')) return 0;
        parts[i] = value;
        if (i < 2 && *version++ != '.') return 0;
    }
    if (!strncmp(version, "-rc.", 4)) {
        version += 4;
        if (*version < '1' || *version > '9') return 0;
        while (*version >= '0' && *version <= '9') version++;
    }
    if (*version == '+') {
        const char* start = ++version;
        while ((*version >= '0' && *version <= '9') || (*version >= 'a' && *version <= 'z') ||
               (*version >= 'A' && *version <= 'Z') || *version == '.' || *version == '-') version++;
        if (version == start) return 0;
    }
    if (*version) return 0;
    return parts[0] >= 2;
}

static inline int selectPyroWaveFormat(int formats, unsigned int serverFormats, const char* sdp) {
    // Match a complete attribute: a future bitstream revision must use the ordinary codecs.
    int codec = 0, bitstream = 0;
    for (const char* line = sdp; *line;) {
        const char* end = strchr(line, '\n');
        size_t length = end ? (size_t)(end - line) : strlen(line);
        if (length && line[length - 1] == '\r') length--;
        const char* revision = "a=x-ss-pyrowave.bitstream:186f0393";
        if (length == strlen(revision) && !memcmp(line, revision, length)) bitstream = 1;
        if (length > 9 && !memcmp(line, "a=rtpmap:", 9)) {
            const char* payload = line + 9;
            while (payload < line + length && *payload >= '0' && *payload <= '9') payload++;
            const char* format = " PYROWAVE/90000";
            if (payload > line + 9 && (size_t)(line + length - payload) == strlen(format) &&
                !memcmp(payload, format, strlen(format))) codec = 1;
        }
        if (!end) break;
        line = end + 1;
    }
    if (!codec || !bitstream) return 0;
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
