package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VideoStatsTest {
    @Test
    public void missingHostTimingDoesNotEraseObservedMinimum() {
        VideoStats stats = new VideoStats();
        stats.minHostProcessingLatency = 50;
        stats.add(new VideoStats());
        assertEquals(50, stats.minHostProcessingLatency);
    }

    @Test
    public void accumulatedHostTimingDoesNotOverflowAnInteger() {
        VideoStats stats = new VideoStats();
        VideoStats window = new VideoStats();
        window.totalHostProcessingLatency = 1000000;
        for (int i = 0; i < 3000; i++) {
            stats.add(window);
        }
        assertEquals(3000000000L, stats.totalHostProcessingLatency);
    }
}
