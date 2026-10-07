package com.limelight.nvstream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AdaptiveBitrateControllerTest {
    @Test
    public void sustainedLossReducesBitrateWithCooldownAndFloor() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(10000, 10000, 0);
        assertEquals(0, controller.sample(1000, true, true, 0));
        assertEquals(0, controller.sample(4000, true, true, 0));
        assertEquals(8000, controller.sample(5000, true, true, 0));
        controller.applied(8000, 8000, 5000);
        assertEquals(0, controller.sample(6000, true, true, 0));
        assertEquals(6400, controller.sample(10000, true, true, 0));
        controller.applied(2000, 2000, 10000);
        assertEquals(0, controller.sample(15000, true, true, 0));
        assertEquals(0, controller.sample(16000, true, true, 0));
    }

    @Test
    public void frameLossCanTriggerReductionWithoutNativeWarning() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(20000, 20000, 0);
        assertEquals(0, controller.sample(5000, true, false, 4));
        assertEquals(16000, controller.sample(6000, true, false, 4));
    }

    @Test
    public void nativeCongestionCanReduceBitrateWhenFramesStopArriving() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(10000, 10000, 0);
        assertEquals(0, controller.sample(5000, false, true, 0));
        assertEquals(8000, controller.sample(6000, false, true, 0));
        controller.applied(8000, 8000, 6000);
        assertEquals(0, controller.sample(30000, false, false, 0));
    }

    @Test
    public void recoveryRequiresHealthyVideoAndNeverExceedsChosenCeiling() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(10000, 9800, 0);
        assertEquals(0, controller.sample(1000, true, false, 0));
        assertEquals(0, controller.sample(15000, true, false, 0));
        assertEquals(10000, controller.sample(16000, true, false, 0));
        controller.applied(10000, 10000, 16000);
        assertEquals(0, controller.sample(17000, true, false, 0));
        assertEquals(0, controller.sample(32000, true, false, 0));
    }

    @Test
    public void missingVideoAndBorderlineLossResetRecoveryWindow() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(10000, 8000, 0);
        assertEquals(0, controller.sample(1000, true, false, 0));
        assertEquals(0, controller.sample(15000, false, false, 0));
        assertEquals(0, controller.sample(16000, true, false, 0));
        assertEquals(0, controller.sample(31000, true, false, 1));
        assertEquals(0, controller.sample(32000, true, false, 0));
        assertEquals(8400, controller.sample(47000, true, false, 0));
    }

    @Test
    public void hostCapIsRespectedEvenBelowNormalFloor() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(10000, 10000, 0);
        controller.applied(8000, 1500, 5000);
        assertEquals(0, controller.sample(6000, true, false, 0));
        assertEquals(0, controller.sample(21000, true, false, 0));
        assertEquals(0, controller.sample(22000, true, true, 10));
        assertEquals(0, controller.sample(23000, true, true, 10));
    }

    @Test
    public void smallStartupBitrateIsNeverRaisedToTheFloor() {
        AdaptiveBitrateController controller = new AdaptiveBitrateController(1000, 1000, 0);
        assertEquals(0, controller.sample(5000, true, true, 20));
        assertEquals(0, controller.sample(6000, true, true, 20));
    }
}
