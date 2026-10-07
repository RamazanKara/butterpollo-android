package com.limelight.nvstream;

public final class AdaptiveBitrateController {
    private int ceilingKbps;
    private int currentKbps;
    private int floorKbps;
    private long lastChangeMs;
    private long healthySinceMs = -1;
    private int poorSamples;

    public AdaptiveBitrateController(int ceilingKbps, int currentKbps, long nowMs) {
        this.ceilingKbps = ceilingKbps;
        this.currentKbps = Math.min(currentKbps, ceilingKbps);
        floorKbps = Math.min(2000, this.currentKbps);
        lastChangeMs = nowMs;
    }

    // Called once per second, only while no bitrate request is in flight.
    public int sample(long nowMs, boolean recentVideo, boolean poorConnection, float lossPercent) {
        if (!recentVideo && !poorConnection) {
            healthySinceMs = -1;
            poorSamples = 0;
            return 0;
        }
        if (poorConnection || lossPercent >= 3) {
            healthySinceMs = -1;
            poorSamples++;
            if (poorSamples >= 2 && nowMs - lastChangeMs >= 5000) {
                int target = Math.max(floorKbps, currentKbps * 80 / 100);
                return target < currentKbps ? target : 0;
            }
        } else {
            poorSamples = 0;
            if (lossPercent >= 0.5f) {
                healthySinceMs = -1;
            } else if (healthySinceMs == -1) {
                healthySinceMs = nowMs;
            } else if (nowMs - healthySinceMs >= 15000 && nowMs - lastChangeMs >= 15000) {
                int target = Math.min(ceilingKbps, currentKbps + Math.max(250, currentKbps / 20));
                return target > currentKbps ? target : 0;
            }
        }
        return 0;
    }

    public void applied(int requestedKbps, int appliedKbps, long nowMs) {
        // The host may enforce a lower cap. Do not repeatedly probe beyond it.
        if (appliedKbps < requestedKbps) {
            ceilingKbps = Math.min(ceilingKbps, appliedKbps);
        }
        currentKbps = appliedKbps;
        floorKbps = Math.min(floorKbps, appliedKbps);
        lastChangeMs = nowMs;
        healthySinceMs = -1;
        poorSamples = 0;
    }
}
