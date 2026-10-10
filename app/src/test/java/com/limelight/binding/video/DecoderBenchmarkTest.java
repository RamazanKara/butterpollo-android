package com.limelight.binding.video;

import org.junit.Test;

import static org.junit.Assert.*;

public class DecoderBenchmarkTest {
    @Test public void clearlySlowerCodecsAreSkippedButNeverH264() {
        boolean[] skip = DecoderBenchmark.slower(new long[] {2000, 2300, 4000}, false);
        assertFalse(skip[0]);
        assertFalse(skip[1]);
        assertTrue(skip[2]);
        assertFalse(DecoderBenchmark.slower(new long[] {1000, 5000, 5000}, false)[0]);
    }

    @Test public void smallDifferencesKeepEveryCodec() {
        boolean[] skip = DecoderBenchmark.slower(new long[] {3000, 3600, 3400}, false);
        assertFalse(skip[1]);
        assertFalse(skip[2]);
    }

    @Test public void hdrComparesOnlyHdrCapableCodecs() {
        boolean[] skip = DecoderBenchmark.slower(new long[] {1000, 3000, 2500}, true);
        assertFalse(skip[1]);
        assertFalse(skip[2]);
    }

    @Test public void unmeasuredCodecsAreKept() {
        boolean[] skip = DecoderBenchmark.slower(new long[] {2000, 0, 0}, false);
        assertFalse(skip[1]);
        assertFalse(skip[2]);
    }
}
