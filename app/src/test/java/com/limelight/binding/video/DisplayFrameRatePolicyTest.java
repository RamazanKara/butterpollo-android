package com.limelight.binding.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayFrameRatePolicyTest {
    @Test
    public void android15UsesOnlySurfaceHintsWithoutClaimingCapabilityDetection() {
        assertTrue(DisplayFrameRatePolicy.useAdaptiveHints(35, true, false, false));
        assertFalse(DisplayFrameRatePolicy.useAdaptiveHints(35, true, true, false));
    }

    @Test
    public void android16RequiresArrSupportForBothSurfaces() {
        for (boolean texture : new boolean[] {false, true}) {
            assertTrue(DisplayFrameRatePolicy.useAdaptiveHints(36, true, texture, true));
            assertFalse(DisplayFrameRatePolicy.useAdaptiveHints(36, true, texture, false));
            assertFalse(DisplayFrameRatePolicy.useAdaptiveHints(36, false, texture, true));
            assertFalse(DisplayFrameRatePolicy.useAdaptiveHints(34, true, texture, true));
        }
    }

    @Test
    public void cadencePreservesFractionalRatesAndClearsMissingMeasurements() {
        assertEquals(59.94f, DisplayFrameRatePolicy.cadenceVote(59.94f, 60), 0);
        assertEquals(120, DisplayFrameRatePolicy.cadenceVote(180, 120), 0);
        for (float unavailable : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(0, DisplayFrameRatePolicy.cadenceVote(unavailable, 60), 0);
        }
    }
}
