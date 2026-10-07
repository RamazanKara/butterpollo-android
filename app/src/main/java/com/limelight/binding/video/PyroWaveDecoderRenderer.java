package com.limelight.binding.video;

import android.os.Build;
import android.view.Surface;

import com.limelight.LimeLog;
import com.limelight.nvstream.jni.MoonBridge;

// Adapted from joemossjr16/artemis-android-pyrowave 387d3a5c (GPL-3.0).
final class PyroWaveDecoderRenderer {
    private static final boolean LIBRARY_LOADED = loadLibrary();
    private long handle;
    private int format;

    private static boolean loadLibrary() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            System.loadLibrary("pyrowave-renderer");
            return true;
        } catch (UnsatisfiedLinkError e) {
            LimeLog.info("PyroWave unavailable: " + e.getMessage());
            return false;
        }
    }

    static boolean isAvailable() {
        return LIBRARY_LOADED && nativeIsAvailable();
    }

    synchronized boolean setup(Surface surface, int format, int width, int height, int fps, boolean fullRange) {
        cleanup();
        if (!LIBRARY_LOADED || surface == null || !surface.isValid()) return false;
        handle = nativeCreate(surface, width, height, fps,
                (format & MoonBridge.VIDEO_FORMAT_MASK_YUV444) != 0,
                (format & MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0, fullRange);
        this.format = handle != 0 ? format : 0;
        return handle != 0;
    }

    synchronized int getFormat() {
        return format;
    }

    // CLOCK_MONOTONIC at the decode fence, before acquiring/presenting a swapchain image.
    synchronized long submitFrame(byte[] data, int length) {
        return handle != 0 ? nativeSubmitFrame(handle, data, length) : -1;
    }

    synchronized int getLastGpuDecodeUs() {
        return handle != 0 ? nativeGetLastGpuDecodeUs(handle) : 0;
    }

    synchronized void setHdrMode(boolean enabled, byte[] metadata) {
        if (handle != 0) nativeSetHdrMode(handle, enabled, metadata);
    }

    synchronized void cleanup() {
        if (handle != 0) nativeDestroy(handle);
        handle = 0;
        format = 0;
    }

    private static native boolean nativeIsAvailable();
    private static native long nativeCreate(Surface surface, int width, int height, int fps,
                                           boolean chroma444, boolean tenBit, boolean fullRange);
    private static native long nativeSubmitFrame(long handle, byte[] data, int length);
    private static native int nativeGetLastGpuDecodeUs(long handle);
    private static native void nativeSetHdrMode(long handle, boolean enabled, byte[] metadata);
    private static native void nativeDestroy(long handle);
}
