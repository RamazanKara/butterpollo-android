package com.limelight.binding.video;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class PerformanceOverlayTest {
    private String compact(float shownFps, float networkMs, float decodeMs, float loss) {
        return PerformanceOverlay.formatCompactStats(Locale.US, shownFps, networkMs, decodeMs,
                loss, "%1$.1f%% loss");
    }

    @Test
    public void roundsShownFpsTotalLatencyAndFrameLossOnOneLine() {
        assertEquals("● 59.9 FPS · 13 ms · 0.1% loss", compact(59.94f, 8, 4.6f, 0.14f));
        assertEquals("● 120.0 FPS · 13 ms · 1.3% loss", compact(119.96f, 8.3f, 4.3f, 1.25f));
        assertEquals("● 0.0 FPS · 0 ms · 0.0% loss", compact(0, 0, 0, 0));
    }

    @Test
    public void hidesMissingMeasurementsWithoutDanglingSeparators() {
        assertEquals("● 12 ms · 0.0% loss", compact(-1, 8, 4, 0));
        assertEquals("● 60.0 FPS · 0.0% loss", compact(60, -1, 4, 0));
        assertEquals("● 60.0 FPS · 0.0% loss", compact(60, 8, -1, 0));
        assertEquals("● 60.0 FPS · 12 ms", compact(60, 8, 4, -1));
        assertEquals("● 60.0 FPS", compact(60, -1, -1, -1));
        assertEquals("● 12 ms", compact(-1, 8, 4, -1));
        assertEquals("● 0.0% loss", compact(-1, -1, -1, 0));
        assertEquals("", compact(-1, -1, -1, -1));
    }

    @Test
    public void nonFiniteMeasurementsAreUnavailable() {
        for (float missing : new float[] {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertEquals("", compact(missing, missing, missing, missing));
            assertEquals("● 60.0 FPS · 0.0% loss", compact(60, missing, 4, 0));
            assertEquals("● 60.0 FPS · 0.0% loss", compact(60, 8, missing, 0));
        }
    }

    @Test
    public void germanUsesLocalizedNumbersAndLossLabel() {
        assertEquals("● 59,9 FPS · 13 ms · 0,1% Verlust",
                PerformanceOverlay.formatCompactStats(Locale.GERMANY, 59.94f, 8, 4.6f, 0.14f,
                        "%1$.1f%% Verlust"));
    }

    @Test
    public void pyroWaveUsesShownFramesAndCompletedDecodeTime() {
        FrameLatencyStats stats = new FrameLatencyStats();
        long startNs = 1000000000L;
        stats.onFrameReceived(startNs);
        for (int i = 1; i <= 120; i++) {
            long receiveNs = startNs + i * 16666666L;
            stats.onFrameReceived(receiveNs);
            stats.onDecoderInput(i, i, receiveNs, receiveNs, receiveNs, (char) 0);
            stats.onDecoderOutput(0, i, receiveNs + 4000000L);
            stats.onOutputReleased(0, receiveNs + 5000000L, true, true);
            if (i % 2 == 0) stats.onFrameRendered(i, receiveNs + 8000000L);
        }
        float shownFps = stats.getFrameRates(startNs + 2020000000L)[2];
        assertEquals("● 30.0 FPS · 12 ms · 1.2% loss",
                compact(shownFps, 8, stats.takeDecodeTimeMs(), 1.2f));
        assertEquals("● 0.0% loss", compact(-1, -1, -1, 0));
    }

    @Test
    public void unavailablePresentationTimingDoesNotSubstituteReceivedFps() {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onFrameReceived(1000000000L);
        stats.onDecoderInput(1, 1, 1000000000L, 1000000000L, 1000000000L, (char) 0);
        stats.onDecoderOutput(0, 1, 1004000000L);
        stats.onOutputReleased(0, 1005000000L, true, false);
        assertEquals("● 12 ms · 0.0% loss",
                compact(stats.getFrameRates(2000000000L)[2], 8, stats.takeDecodeTimeMs(), 0));
    }

    @Test
    public void pyroWaveWithoutDisplayTimingHidesShownFpsFromFirstFrame() {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onFrameReceived(1000000000L);
        stats.onDecoderInput(1, 1, 1000000000L, 1000000000L, 1000000000L, (char) 0);
        stats.onDecoderOutput(0, 1, 1004000000L);
        stats.onOutputReleased(0, 1005000000L, true, true);
        stats.discard(1, "render_unavailable");
        assertEquals("● 12 ms · 0.0% loss",
                compact(stats.getFrameRates(2000000000L)[2], 8, stats.takeDecodeTimeMs(), 0));
    }

    @Test
    public void healthUsesUnroundedLatencyAndFrameLossAndMarksMissingDataAmber() {
        assertEquals(0xff69db7c, PerformanceOverlay.healthColor(25, 4.99f, 0.99f));
        assertEquals(0xffffc857, PerformanceOverlay.healthColor(25, 5, 0));
        assertEquals(0xffffc857, PerformanceOverlay.healthColor(8, 4, 1));
        assertEquals(0xffff6b6b, PerformanceOverlay.healthColor(55, 5, 0));
        assertEquals(0xffff6b6b, PerformanceOverlay.healthColor(8, 4, 5));
        assertEquals(0xffffc857, PerformanceOverlay.healthColor(-1, 4, 0));
        assertEquals(0xffffc857, PerformanceOverlay.healthColor(8, -1, 0));
        assertEquals(0xffffc857, PerformanceOverlay.healthColor(8, 4, Float.NaN));
        assertEquals(0xffff6b6b, PerformanceOverlay.healthColor(-1, -1, 5));
    }
}
