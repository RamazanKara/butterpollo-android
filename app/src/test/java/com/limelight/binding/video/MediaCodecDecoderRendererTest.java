package com.limelight.binding.video;

import android.media.MediaCodecInfo.CodecProfileLevel;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.limelight.nvstream.jni.MoonBridge.*;
import static org.junit.Assert.*;

public class MediaCodecDecoderRendererTest {
    @Test
    public void main10AloneIsNotAnHdrDisplayPipeline() {
        assertEquals(VIDEO_FORMAT_H265, MediaCodecDecoderRenderer.videoFormatsForProfiles("video/hevc",
                new int[] {CodecProfileLevel.HEVCProfileMain10}, true, true));
        assertEquals(VIDEO_FORMAT_AV1_MAIN8, MediaCodecDecoderRenderer.videoFormatsForProfiles("video/av01",
                new int[] {CodecProfileLevel.AV1ProfileMain10}, true, true));
    }

    @Test
    public void hdr10AndHdr10PlusDecodersCanReceiveStaticHdr10() {
        for (int profile : new int[] {CodecProfileLevel.HEVCProfileMain10HDR10, CodecProfileLevel.HEVCProfileMain10HDR10Plus}) {
            assertEquals(VIDEO_FORMAT_H265 | VIDEO_FORMAT_H265_MAIN10,
                    MediaCodecDecoderRenderer.videoFormatsForProfiles("video/hevc", new int[] {profile}, true, false));
        }
        for (int profile : new int[] {CodecProfileLevel.AV1ProfileMain10HDR10, CodecProfileLevel.AV1ProfileMain10HDR10Plus}) {
            assertEquals(VIDEO_FORMAT_AV1_MAIN8 | VIDEO_FORMAT_AV1_MAIN10,
                    MediaCodecDecoderRenderer.videoFormatsForProfiles("video/av01", new int[] {profile}, true, false));
        }
    }

    @Test
    public void sdrDisplayNeverAdvertisesHdr() {
        assertEquals(VIDEO_FORMAT_H265, MediaCodecDecoderRenderer.videoFormatsForProfiles("video/hevc",
                new int[] {CodecProfileLevel.HEVCProfileMain10HDR10}, false, false));
    }

    @Test
    public void onlyExplicit444ProfilesAdvertise444() {
        assertEquals(VIDEO_FORMAT_H264, MediaCodecDecoderRenderer.videoFormatsForProfiles("video/avc",
                new int[] {CodecProfileLevel.AVCProfileHigh}, false, true));
        assertEquals(VIDEO_FORMAT_H264 | VIDEO_FORMAT_H264_HIGH8_444,
                MediaCodecDecoderRenderer.videoFormatsForProfiles("video/avc",
                        new int[] {CodecProfileLevel.AVCProfileHigh444}, false, true));
        assertEquals(VIDEO_FORMAT_H265 | VIDEO_FORMAT_H265_REXT8_444,
                MediaCodecDecoderRenderer.videoFormatsForProfiles("video/hevc",
                        new int[] {CodecProfileLevel.HEVCProfileMain444}, false, true));
        assertEquals(VIDEO_FORMAT_H265, MediaCodecDecoderRenderer.videoFormatsForProfiles("video/hevc",
                new int[] {CodecProfileLevel.HEVCProfileMain444}, false, false));
    }

    @Test
    public void av1MainAndHevcMain10CannotClaimHdr444() {
        int formats = MediaCodecDecoderRenderer.videoFormatsForProfiles("video/av01",
                new int[] {CodecProfileLevel.AV1ProfileMain10HDR10}, true, true) |
                MediaCodecDecoderRenderer.videoFormatsForProfiles("video/hevc",
                        new int[] {CodecProfileLevel.HEVCProfileMain10HDR10}, true, true);
        assertEquals(0, formats & VIDEO_FORMAT_MASK_YUV444);
    }

    @Test
    public void hostMetadataKeepsWireUnitsAndExcludesFullFrameExtension() {
        // rust/core/src/hdr.rs: Metadata::display(1499.9, 0.002, 800.0).wire(true), excluding enabled.
        int[] values = {35400, 14600, 8500, 39850, 6550, 2300, 15635, 16450, 1499, 20, 0, 0, 800};
        ByteBuffer wire = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) {
            wire.putShort((short) value);
        }
        ByteBuffer info = MediaCodecDecoderRenderer.createHdrStaticInfo(wire.array()).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(25, info.remaining());
        assertEquals(0, info.get());
        for (int i = 0; i < 12; i++) {
            assertEquals(values[i], Short.toUnsignedInt(info.getShort()));
        }
        assertFalse(info.hasRemaining());
    }

    @Test
    public void missingOrTruncatedMetadataIsNotPassedToMediaCodec() {
        assertNull(MediaCodecDecoderRenderer.createHdrStaticInfo(null));
        assertNull(MediaCodecDecoderRenderer.createHdrStaticInfo(new byte[23]));
        assertEquals(25, MediaCodecDecoderRenderer.createHdrStaticInfo(new byte[24]).remaining());
    }
}
