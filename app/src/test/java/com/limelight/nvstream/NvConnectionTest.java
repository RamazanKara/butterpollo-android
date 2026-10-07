package com.limelight.nvstream;

import org.junit.Test;

import static com.limelight.nvstream.jni.MoonBridge.*;
import static org.junit.Assert.*;

public class NvConnectionTest {
    private static final int CLIENT_HDR = VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 |
            VIDEO_FORMAT_H265_MAIN10 | VIDEO_FORMAT_AV1_MAIN8 | VIDEO_FORMAT_AV1_MAIN10;
    private static final int PYROWAVE_CLIENT_FORMATS = 0x0F0000;
    private static final int PYROWAVE_HOST_FORMATS = 0x07800000;

    @Test
    public void unknownCodecFamiliesNeverReachTheNativeHandshake() {
        int host = PYROWAVE_HOST_FORMATS | 0x30301;
        assertEquals(CLIENT_HDR | VIDEO_FORMAT_PYROWAVE_MAIN10 | VIDEO_FORMAT_PYROWAVE_MAIN10_444,
                NvConnection.negotiateVideoFormats(CLIENT_HDR | PYROWAVE_CLIENT_FORMATS | 0x40000000, host));
        assertEquals(PYROWAVE_CLIENT_FORMATS, NvConnection.negotiateVideoFormats(PYROWAVE_CLIENT_FORMATS, host));
    }

    @Test
    public void pyrowaveProfilesRequireTheirOwnHostBits() {
        int[] client = { VIDEO_FORMAT_PYROWAVE, VIDEO_FORMAT_PYROWAVE_444,
                VIDEO_FORMAT_PYROWAVE_MAIN10, VIDEO_FORMAT_PYROWAVE_MAIN10_444 };
        int[] host = { 0x00800000, 0x01000000, 0x02000000, 0x04000000 };
        for (int i = 0; i < client.length; i++) {
            assertEquals(client[i], NvConnection.negotiateVideoFormats(PYROWAVE_CLIENT_FORMATS, host[i]));
        }
        assertEquals(0, NvConnection.negotiateVideoFormats(PYROWAVE_CLIENT_FORMATS, 0x30301));
    }

    @Test
    public void surfaceFailureRetainsConventionalFallback() {
        int common = NvConnection.negotiateVideoFormats(CLIENT_HDR | PYROWAVE_CLIENT_FORMATS, 0x07830301);
        assertEquals(CLIENT_HDR, NvConnection.negotiateVideoFormats(common & ~VIDEO_FORMAT_MASK_PYROWAVE, 0x07830301));
        // PyroWave HDR must not erase ordinary SDR codecs needed on a bitstream mismatch.
        assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 | VIDEO_FORMAT_AV1_MAIN8 | VIDEO_FORMAT_PYROWAVE_MAIN10,
                NvConnection.negotiateVideoFormats(CLIENT_HDR | VIDEO_FORMAT_PYROWAVE_MAIN10, 0x02010101));
    }

    @Test
    public void conventionalHdrWinsOverPyrowaveSdr() {
        assertEquals(CLIENT_HDR, NvConnection.negotiateVideoFormats(CLIENT_HDR |
                VIDEO_FORMAT_PYROWAVE | VIDEO_FORMAT_PYROWAVE_444, 0x01830301));
    }

    @Test
    public void pyrowaveHostStillUsesAv1OrHevcHdrWithOrdinaryClients() {
        for (int extension : new int[] {0x00800000, 0x01000000, 0x02000000, 0x04000000,
                PYROWAVE_HOST_FORMATS}) {
            assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_AV1_MAIN8 | VIDEO_FORMAT_AV1_MAIN10,
                    NvConnection.negotiateVideoFormats(CLIENT_HDR, extension | 0x30101));
            assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 | VIDEO_FORMAT_H265_MAIN10,
                    NvConnection.negotiateVideoFormats(CLIENT_HDR, extension | 0x10301));
        }
    }

    @Test
    public void pyrowaveHdrAnd444DoNotImplyConventionalHdrOr444() {
        int client = VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265 | VIDEO_FORMAT_H265_MAIN10 |
                VIDEO_FORMAT_H264_HIGH8_444 | VIDEO_FORMAT_H265_REXT8_444;
        assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H265,
                NvConnection.negotiateVideoFormats(client, PYROWAVE_HOST_FORMATS | 0x101));
        assertEquals(VIDEO_FORMAT_H264,
                NvConnection.negotiateVideoFormats(VIDEO_FORMAT_H264, PYROWAVE_HOST_FORMATS | 1));
    }

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
