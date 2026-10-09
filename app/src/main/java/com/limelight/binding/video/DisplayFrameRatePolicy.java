package com.limelight.binding.video;

import android.os.Build;
import android.view.Display;

public final class DisplayFrameRatePolicy {
    public static boolean useAdaptiveHints(int sdk, boolean vrr, boolean arrSupported) {
        return vrr && sdk >= 36 && arrSupported;
    }

    public static float cadenceVote(float measuredFps, float panelMaxHz, boolean useArr) {
        return useArr && Float.isFinite(measuredFps) && measuredFps > 0 ?
                Math.min(measuredFps, panelMaxHz) : panelMaxHz;
    }

    public static float maxRefreshRate(Display display) {
        float maximum;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            maximum = display.getMode().getRefreshRate();
            for (Display.Mode mode : display.getSupportedModes()) {
                maximum = Math.max(maximum, mode.getRefreshRate());
            }
        } else {
            maximum = display.getRefreshRate();
            for (float rate : display.getSupportedRefreshRates()) {
                maximum = Math.max(maximum, rate);
            }
        }
        return maximum;
    }

    public static int streamFrameRate(float panelMaxHz) {
        // Match the host's 1–1000 Hz launch range.
        return Math.max(1, Math.min(1000, Math.round(panelMaxHz)));
    }
}
