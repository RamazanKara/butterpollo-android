package com.limelight.binding.video;

import org.junit.Test;
import static com.limelight.preferences.PreferenceConfiguration.*;
import static org.junit.Assert.*;

public class FramePacingPolicyTest {
    @Test public void smoothnessModesKeepQueuedFramesAndLatencyModesDropStaleFrames() {
        assertFalse(FramePacingPolicy.dropQueuedFrames(FRAME_PACING_MAX_SMOOTHNESS));
        assertFalse(FramePacingPolicy.dropQueuedFrames(FRAME_PACING_CAP_FPS));
        assertTrue(FramePacingPolicy.dropQueuedFrames(FRAME_PACING_MIN_LATENCY));
        assertTrue(FramePacingPolicy.dropQueuedFrames(FRAME_PACING_BALANCED));
    }

    @Test public void sixtyFpsOnNinetyHzDoesNotCollapseToFortyFiveFps() {
        long due = 0;
        int frames = 0;
        for (int tick = 0; tick < 900; tick++) {
            long vsync = 1_000_000_000L + Math.round(tick * 1_000_000_000.0 / 90);
            if (vsync >= due) {
                frames++;
                due = FramePacingPolicy.nextFrameTimeNs(due, vsync, 60);
            }
        }
        assertEquals(600, frames);
    }

    @Test public void lateCallbackSkipsMissedSlotsInsteadOfBuildingCatchUpQueue() {
        long due = FramePacingPolicy.nextFrameTimeNs(1_000_000_000L, 1_250_000_000L, 100);
        assertEquals(1_260_000_000L, due);
    }

    @Test public void fractionalPanelRatesPreserveStreamCadence() {
        long due = 0;
        int frames = 0;
        for (int tick = 0; tick < 1199; tick++) {
            long vsync = 1_000_000_000L + Math.round(tick * 1_000_000_000.0 / 119.88);
            if (vsync >= due) {
                frames++;
                due = FramePacingPolicy.nextFrameTimeNs(due, vsync, 60);
            }
        }
        assertEquals(600, frames);
    }
}
