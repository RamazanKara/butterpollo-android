package com.limelight.nvstream;

public final class PyroWaveBitrateController {
    public static final int MAX_RUNTIME_KBPS = 500000;
    private int ceilingKbps;
    private int currentKbps;
    private int floorKbps;
    private final float frameMs;
    private long lastChangeMs;
    private long healthySinceMs = -1;
    private int congestedSamples;
    private float previousLoss = -1;
    private float previousQueueMs = -1;

    public PyroWaveBitrateController(int ceilingKbps, int currentKbps, int fps, long nowMs) {
        this.ceilingKbps = Math.min(MAX_RUNTIME_KBPS, ceilingKbps);
        this.currentKbps = Math.min(this.ceilingKbps, currentKbps);
        floorKbps = Math.min(10000, this.currentKbps);
        frameMs = 1000.0f / fps;
        lastChangeMs = nowMs;
    }

    // Sample once per second while no runtime request is pending. Loss includes recovered frames' missing records.
    public int sample(long nowMs, boolean recentVideo, boolean poorConnection, float lossPercent, float queueMs, float decodeMs) {
        if (!recentVideo) {
            healthySinceMs = -1;
            previousLoss = previousQueueMs = -1;
            if (!poorConnection) {
                congestedSamples = 0;
                return 0;
            }
        }
        boolean congested = poorConnection || (recentVideo && (lossPercent >= 2 || decodeMs >= frameMs ||
                queueMs >= Math.max(20, frameMs * 2) ||
                (previousLoss >= 0 && lossPercent >= 0.5f && lossPercent - previousLoss >= 0.25f) ||
                (previousQueueMs >= 0 && queueMs >= Math.max(8, frameMs * 0.75f) &&
                        queueMs - previousQueueMs >= 3)));
        previousLoss = lossPercent;
        previousQueueMs = queueMs;
        if (congested) {
            healthySinceMs = -1;
            if (++congestedSamples >= 2 && nowMs - lastChangeMs >= 3000) {
                int target = Math.max(floorKbps, currentKbps * 85 / 100);
                return target < currentKbps ? target : 0;
            }
        } else {
            congestedSamples = 0;
            if (lossPercent >= 0.1f || queueMs >= Math.max(4, frameMs / 2) ||
                    decodeMs < 0 || decodeMs >= frameMs * 0.75f) {
                healthySinceMs = -1;
            } else if (healthySinceMs == -1) {
                healthySinceMs = nowMs;
            } else if (nowMs - healthySinceMs >= 20000 && nowMs - lastChangeMs >= 20000) {
                int target = Math.min(ceilingKbps, currentKbps + Math.max(250, currentKbps / 50));
                return target > currentKbps ? target : 0;
            }
        }
        return 0;
    }

    public void applied(int requestedKbps, int appliedKbps, long nowMs) {
        if (appliedKbps < requestedKbps) ceilingKbps = Math.min(ceilingKbps, appliedKbps);
        currentKbps = Math.min(MAX_RUNTIME_KBPS, appliedKbps);
        floorKbps = Math.min(floorKbps, currentKbps);
        lastChangeMs = nowMs;
        healthySinceMs = -1;
        congestedSamples = 0;
        previousLoss = previousQueueMs = -1;
    }

    public void suspend() {
        healthySinceMs = -1;
        congestedSamples = 0;
        previousLoss = previousQueueMs = -1;
    }
}
