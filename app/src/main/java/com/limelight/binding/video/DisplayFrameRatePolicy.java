package com.limelight.binding.video;

public final class DisplayFrameRatePolicy {
    public static boolean useAdaptiveHints(int sdk, boolean vrr, boolean textureView, boolean arrSupported) {
        // Android 15 has Surface votes but no public ARR capability query or View vote API.
        return vrr && (sdk >= 36 ? arrSupported : sdk == 35 && !textureView);
    }

    public static float cadenceVote(float measuredFps, float targetFps) {
        return Float.isFinite(measuredFps) && measuredFps > 0 ? Math.min(measuredFps, targetFps) : 0;
    }
}
