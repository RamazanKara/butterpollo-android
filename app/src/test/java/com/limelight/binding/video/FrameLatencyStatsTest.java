package com.limelight.binding.video;

import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.*;

public class FrameLatencyStatsTest {
    @Test
    public void hostDurationIsIndependentOfClientClockAndSurvivesCsvExport() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(42, 123, 1000000, 2000000, (char) 123);
        stats.onDecoderOutput(0, 123, 5000000);
        stats.onOutputReleased(0, 6000000, true, true);
        stats.onFrameRendered(123, 7000000);
        assertEquals(6.0, stats.summarize()[3][1], 0.00001);
        assertArrayEquals(new double[] {1, 12.3, 12.3, 12.3}, stats.summarize()[4], 0.00001);
        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().endsWith(",12.300000\n"));
        assertEquals(FrameLatencyStats.CSV_HEADER.trim().split(",").length, csv.toString().trim().split(",").length);
    }

    @Test
    public void missingHostTimingIsBlankNotZeroLatency() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 1, 100, 200, (char) 0);
        stats.discardPending("stopped");
        assertEquals(0, stats.summarize()[4][0], 0);
        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().endsWith(",\n"));
    }

    @Test
    public void wireTimingIsUnsignedAndWindowIsBounded() {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 1, 100, 200, (char) 65535);
        assertEquals(6553.5, stats.summarize()[4][1], 0.00001);
        stats.discardPending("stopped");
        for (int i = 0; i < FrameLatencyStats.WINDOW_SIZE; i++) {
            stats.onDecoderInput(i, i, 100, 200, (char) 10);
            stats.discardPending("stopped");
        }
        assertArrayEquals(new double[] {600, 1, 1, 1}, stats.summarize()[4], 0.00001);
    }

    @Test
    public void hostPercentilesUseObservedSamplesOnly() {
        FrameLatencyStats stats = new FrameLatencyStats();
        for (int i = 0; i <= 100; i++) {
            stats.onDecoderInput(i, i, 100, 200, (char) i);
            stats.discardPending("stopped");
        }
        assertArrayEquals(new double[] {100, 5.05, 9.5, 9.9}, stats.summarize()[4], 0.00001);
    }
}
