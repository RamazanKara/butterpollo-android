package com.limelight.binding.video;

import org.junit.Test;

import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class FrameLatencyStatsTest {
    @Test
    public void reusedOutputIndexKeepsBothPendingRenderCallbacks() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 10, 100, 200, (char) 0);
        stats.onDecoderOutput(0, 10, 300);
        stats.onOutputReleased(0, 400, true, true);
        stats.onDecoderInput(2, 20, 500, 600, (char) 0);
        stats.onDecoderOutput(0, 20, 700);
        stats.onFrameRendered(10, 450);
        stats.onOutputReleased(0, 800, true, true);
        stats.onFrameRendered(20, 900);

        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().contains("1,10,100,200,300,450,400,rendered,"));
        assertTrue(csv.toString().contains("2,20,500,600,700,900,800,rendered,"));
        assertEquals(2, stats.summarize()[3][0], 0);
    }

    @Test
    public void lateCallbacksAfterCodecResetCannotCompleteNewBuffers() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 10, 100, 200, (char) 0);
        stats.onDecoderOutput(0, 10, 300);
        stats.discardPending("codec_reset");
        stats.onDecoderInput(2, 20, 500, 600, (char) 0);
        stats.onDecoderOutput(0, 20, 700);
        stats.onFrameRendered(10, 800);
        stats.onOutputReleased(0, 900, false, true);
        stats.onFrameRendered(20, 1000);

        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().contains("1,10,100,200,300,,,codec_reset,"));
        assertTrue(csv.toString().contains("2,20,500,600,700,,900,dropped,"));
        assertEquals(2, csv.toString().split("\n").length);
        assertEquals(0, stats.summarize()[3][0], 0);
    }

    @Test
    public void absentCallbacksExpireWithoutInventingRenderMeasurements() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 10, 100, 200, (char) 0);
        stats.onDecoderOutput(0, 10, 300);
        stats.onOutputReleased(0, 400, true, true);
        stats.onDecoderInput(2, 20, 100, 200, (char) 0);
        stats.expire(5000000200L);
        stats.onFrameRendered(10, 5000000300L);

        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().contains("render_unobserved"));
        assertTrue(csv.toString().contains("output_unobserved"));
        assertEquals(2, csv.toString().split("\n").length);
        assertEquals(0, stats.summarize()[3][0], 0);
    }

    @Test
    public void invalidClockOrderIsExcludedFromPercentiles() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 10, 300, 200, (char) 0);
        stats.onDecoderOutput(0, 10, 100);
        stats.onOutputReleased(0, 400, true, true);
        stats.onFrameRendered(10, 500);
        for (int stage = 0; stage < 4; stage++) {
            assertEquals(0, stats.summarize()[stage][0], 0);
        }
        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().contains("invalid_render_time"));
    }

    @Test
    public void stalledDecoderAndCsvWriterKeepTrackingBounded() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        for (int i = 0; i < 7000; i++) {
            stats.onDecoderInput(i, i, 100, 200, (char) 0);
        }
        stats.discardPending("stream_ended");
        assertEquals(7000 - 4096, stats.getCsvRowsLost());
        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertEquals(4096, csv.toString().split("\n").length);
        assertTrue(csv.toString().contains("tracking_overflow"));
        assertTrue(csv.toString().contains("stream_ended"));
    }

    @Test
    public void blockedCsvIoDoesNotBlockDecoderCallbacks() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(1, 10, 100, 200, (char) 0);
        stats.discardPending("stream_ended");
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> export = executor.submit(() -> {
                stats.writeCsv(new Writer() {
                    @Override
                    public void write(char[] data, int offset, int length) {
                        writing.countDown();
                        try {
                            assertTrue(resume.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }

                    @Override
                    public void flush() { }

                    @Override
                    public void close() { }
                });
                return null;
            });
            assertTrue(writing.await(5, TimeUnit.SECONDS));
            executor.submit(() -> {
                stats.onDecoderInput(2, 20, 500, 600, (char) 0);
                stats.onDecoderOutput(0, 20, 700);
                stats.onOutputReleased(0, 800, true, true);
                stats.onFrameRendered(20, 900);
            }).get(5, TimeUnit.SECONDS);
            resume.countDown();
            export.get(5, TimeUnit.SECONDS);
            assertEquals(1, stats.summarize()[3][0], 0);
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void completedGpuFenceDoesNotInventDisplayTiming() throws Exception {
        FrameLatencyStats stats = new FrameLatencyStats();
        stats.onDecoderInput(9, 9, 1000000, 2000000, (char) 0);
        stats.onDecoderOutput(0, 9, 3500000);
        stats.onOutputReleased(0, 6000000, true, false);
        assertArrayEquals(new double[] {1, 1.5, 1.5, 1.5}, stats.summarize()[1], 0.00001);
        assertEquals(0, stats.summarize()[2][0], 0);
        assertEquals(0, stats.summarize()[3][0], 0);
        StringWriter csv = new StringWriter();
        stats.writeCsv(csv);
        assertTrue(csv.toString().contains("render_unavailable"));
    }

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
