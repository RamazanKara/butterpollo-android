#include "moonlight-common-c/src/Limelight-internal.h"
#include "pyrowave_protocol.h"

// The core's opaque-frame path already handles the short header and exact FEC payload length.
// Its IDR validation also needs to accept PyroWave picture data.
#undef VIDEO_FORMAT_MASK_AV1
#define VIDEO_FORMAT_MASK_AV1 (0xF000 | VIDEO_FORMAT_MASK_PYROWAVE)
#include "moonlight-common-c/src/VideoDepacketizer.c"
