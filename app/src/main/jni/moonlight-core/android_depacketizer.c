#include "moonlight-common-c/src/Limelight-internal.h"
#include "pyrowave_protocol.h"
#include "../pyrowave-renderer/frame.h"

// The core's opaque-frame path already handles the short header and exact FEC payload length.
// Its IDR validation also needs to accept PyroWave picture data.
#undef VIDEO_FORMAT_MASK_AV1
#define VIDEO_FORMAT_MASK_AV1 (0xF000 | VIDEO_FORMAT_MASK_PYROWAVE)
#define queueRtpPacket queueOrdinaryRtpPacket
#include "moonlight-common-c/src/VideoDepacketizer.c"
#undef queueRtpPacket

static uint8_t pyroLastBlock;
static bool pyroLastBlockEnded;

void finishPyroWaveFrame(void) {
    if (!nalChainHead) return;
    frameType = FRAME_TYPE_IDR;
    waitingForIdrFrame = waitingForNextSuccessfulFrame = false;
    nextFrameNumber++;
    reassembleFrame(nextFrameNumber - 1, false);
    cleanupFrameState();
}

void queueRtpPacket(PRTPV_QUEUE_ENTRY entry) {
    if (!PyroWaveRecordsEnabled) {
        queueOrdinaryRtpPacket(entry);
        return;
    }
    int offset = sizeof(*entry->packet) + ((entry->packet->header & FLAG_EXTENSION) ? 4 : 0);
    PNV_VIDEO_PACKET video = (PNV_VIDEO_PACKET)((char*)entry->packet + offset);
    const int length = entry->length - offset - sizeof(*video);
    uint32_t spi = (video->streamPacketIndex >> 8) & 0xffffff;
    uint8_t block = (video->multiFecBlocks >> 4) & 3;
    uint8_t lastBlock = (video->multiFecBlocks >> 6) & 3;
    uint32_t flags = (video->multiFecFlags & 0x80) ? PYRO_PACKET_RECORD_START : 0;
    if ((video->flags & FLAG_SOF) && block == 0) flags |= PYRO_PACKET_FRAME_START;
    if ((video->flags & FLAG_EOF) && block == lastBlock) flags |= PYRO_PACKET_FRAME_END;
    if (length <= 0 || length > 1400 || nalChainDataLength + length + 8 > PYRO_MAX_FRAME_BYTES) {
        free(entry->packet);
        return;
    }
    if (!nalChainHead) {
        uint32_t marker = 0;
        queueFragment(NULL, (char*)&marker, 0, sizeof(marker));
        nextFrameNumber = video->frameIndex;
        firstPacketReceiveTimeUs = entry->receiveTimeUs;
        firstPacketPresentationTime = entry->presentationTimeUs;
        firstPacketRtpTimestamp = entry->rtpTimestamp;
        const uint8_t *payload = (const uint8_t*)(video + 1);
        frameHostProcessingLatency = (flags & PYRO_PACKET_FRAME_START) && length >= 3 ?
                payload[1] | ((uint16_t)payload[2] << 8) : 0;
    }
    else if ((block == pyroLastBlock && spi == U24(lastPacketInStream + 1)) ||
            (block == pyroLastBlock + 1 && pyroLastBlockEnded && (video->flags & FLAG_SOF))) {
        flags |= PYRO_PACKET_CONTIGUOUS;
    }
    uint8_t header[8];
    pyroWriteLe32(header, (uint32_t)length);
    pyroWriteLe32(header + 4, flags);
    queueFragment(NULL, (char*)header, 0, sizeof(header));
    queueFragment(NULL, (char*)(video + 1), 0, length);
    lastPacketInStream = spi;
    pyroLastBlock = block;
    pyroLastBlockEnded = (video->flags & FLAG_EOF) != 0;
    free(entry->packet);
}
