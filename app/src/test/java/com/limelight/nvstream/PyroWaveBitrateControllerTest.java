package com.limelight.nvstream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PyroWaveBitrateControllerTest {
    @Test
    public void risingRecordLossLowersBeforeSustainedLossThreshold() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(400000, 400000, 60, 0);
        assertEquals(0, c.sample(1000, true, false, 0, 0));
        assertEquals(0, c.sample(2000, true, false, 0.5f, 0));
        assertEquals(340000, c.sample(3000, true, false, 1, 0));
    }

    @Test
    public void risingQueueDelayAndSustainedDelayLowerBitrate() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(300000, 300000, 120, 0);
        assertEquals(0, c.sample(1000, true, false, 0, 4));
        assertEquals(0, c.sample(2000, true, false, 0, 8));
        assertEquals(255000, c.sample(3000, true, false, 0, 12));
        c.applied(255000, 255000, 3000);
        assertEquals(0, c.sample(4000, true, false, 0, 40));
        assertEquals(0, c.sample(5000, true, false, 0, 40));
        assertEquals(216750, c.sample(6000, true, false, 0, 40));
    }

    @Test
    public void transientCongestionDoesNotLowerAndRecoveryIsSlow() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(400000, 300000, 60, 0);
        assertEquals(0, c.sample(4000, true, true, 10, 100));
        assertEquals(0, c.sample(5000, true, false, 0, 0));
        assertEquals(0, c.sample(24000, true, false, 0, 0));
        assertEquals(306000, c.sample(25000, true, false, 0, 0));
        c.applied(306000, 306000, 25000);
        assertEquals(0, c.sample(26000, true, false, 0, 0));
        assertEquals(0, c.sample(45000, true, false, 0, 0));
        assertEquals(312120, c.sample(46000, true, false, 0, 0));
    }

    @Test
    public void runtimeCapAndChosenCeilingAreNeverExceeded() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(2000000, 2000000, 60, 0);
        assertEquals(0, c.sample(1000, true, false, 0, 0));
        assertEquals(0, c.sample(21000, true, false, 0, 0));
        assertEquals(0, c.sample(22000, true, false, 5, 0));
        assertEquals(425000, c.sample(23000, true, false, 5, 0));
        c = new PyroWaveBitrateController(300000, 299000, 60, 0);
        assertEquals(0, c.sample(1000, true, false, 0, 0));
        assertEquals(300000, c.sample(21000, true, false, 0, 0));
    }

    @Test
    public void hostAppliedCapAlsoLowersFloor() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(400000, 400000, 60, 0);
        c.applied(400000, 5000, 1000);
        assertEquals(0, c.sample(2000, true, false, 0, 0));
        assertEquals(0, c.sample(22000, true, false, 0, 0));
        assertEquals(0, c.sample(23000, true, true, 10, 0));
        assertEquals(0, c.sample(24000, true, true, 10, 0));
    }

    @Test
    public void missingVideoAndBorderlineSamplesResetRecovery() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(400000, 300000, 60, 0);
        assertEquals(0, c.sample(1000, true, false, 0, 0));
        assertEquals(0, c.sample(20000, false, false, 0, 0));
        assertEquals(0, c.sample(21000, true, false, 0, 0));
        assertEquals(0, c.sample(40000, true, false, 0.2f, 0));
        assertEquals(0, c.sample(41000, true, false, 0, 0));
        assertEquals(0, c.sample(60000, true, false, 0, 10));
        assertEquals(0, c.sample(61000, true, false, 0, 0));
        assertEquals(306000, c.sample(81000, true, false, 0, 0));
    }

    @Test
    public void nativeCongestionCanReduceWhenVideoStops() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(400000, 400000, 60, 0);
        assertEquals(0, c.sample(5000, false, true, 0, 0));
        assertEquals(340000, c.sample(6000, false, true, 0, 0));
        c.applied(340000, 340000, 6000);
        assertEquals(0, c.sample(30000, false, false, 0, 0));
    }

    @Test
    public void floorDoesNotRaiseSmallInitialBitrate() {
        PyroWaveBitrateController c = new PyroWaveBitrateController(1000, 1000, 60, 0);
        assertEquals(0, c.sample(5000, true, false, 5, 100));
        assertEquals(0, c.sample(6000, true, false, 5, 100));
    }
}
