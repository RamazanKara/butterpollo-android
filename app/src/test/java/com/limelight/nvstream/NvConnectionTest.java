package com.limelight.nvstream;

import org.junit.Test;

import static com.limelight.nvstream.jni.MoonBridge.*;
import static org.junit.Assert.*;

public class NvConnectionTest {
    private static final int CLIENT_HDR = VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 |
            VIDEO_FORMAT_H265_MAIN10 | VIDEO_FORMAT_AV1_MAIN8 | VIDEO_FORMAT_AV1_MAIN10;

    @Test
    public void prefersHevcHdrOverAv1Sdr() {
        int formats = NvConnection.negotiateVideoFormats(CLIENT_HDR, 0x10301);
        assertEquals(0, formats & VIDEO_FORMAT_MASK_AV1);
        assertEquals(VIDEO_FORMAT_H265_MAIN10, formats & VIDEO_FORMAT_MASK_10BIT);
    }

    @Test
    public void av1HdrUsesTheHostBitNotTheClientBit() {
        int formats = NvConnection.negotiateVideoFormats(CLIENT_HDR, 0x30101);
        assertEquals(VIDEO_FORMAT_AV1_MAIN10, formats & VIDEO_FORMAT_MASK_10BIT);
        assertEquals(0, formats & VIDEO_FORMAT_MASK_H265);
    }

    @Test
    public void mismatchedHdrCodecsFallBackToSdr() {
        int formats = NvConnection.negotiateVideoFormats(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 |
                VIDEO_FORMAT_H265_MAIN10, 0x30101);
        assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265, formats);
    }

    @Test
    public void missingHostCapabilitiesDoNotInventHdrOr444() {
        int formats = NvConnection.negotiateVideoFormats(CLIENT_HDR | VIDEO_FORMAT_H264_HIGH8_444 |
                VIDEO_FORMAT_H265_REXT8_444, 0);
        assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 | VIDEO_FORMAT_AV1_MAIN8, formats);
    }

    @Test
    public void prefersSupported444OverSdr420() {
        int formats = NvConnection.negotiateVideoFormats(CLIENT_HDR | VIDEO_FORMAT_H264_HIGH8_444, 0x50101);
        assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H264_HIGH8_444, formats);
    }

    @Test
    public void hdrTakesPriorityOverEightBit444() {
        int formats = NvConnection.negotiateVideoFormats(CLIENT_HDR | VIDEO_FORMAT_H265_REXT8_444, 0xB0101);
        assertEquals(VIDEO_FORMAT_AV1_MAIN10, formats & VIDEO_FORMAT_MASK_10BIT);
        assertEquals(0, formats & VIDEO_FORMAT_MASK_H265);
    }
}
