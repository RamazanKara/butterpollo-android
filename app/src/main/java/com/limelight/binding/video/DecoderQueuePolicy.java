package com.limelight.binding.video;

import java.util.Locale;

final class DecoderQueuePolicy {
    static int outputLimit(String decoderName, boolean advertisedLowLatency, boolean dropLateFrames,
                           boolean recovered) {
        if (dropLateFrames || recovered) return 1;
        String name = decoderName.toLowerCase(Locale.ROOT);
        // Old OMX families in decoder-errata.txt must return scarce output buffers promptly.
        // These are client-held buffers, not codec reference pictures or vendor queue knobs.
        if (name.startsWith("omx.mtk.") || name.startsWith("omx.exynos.") ||
                name.startsWith("omx.nvidia.") || name.startsWith("omx.brcm.") ||
                name.startsWith("omx.ti.") || name.startsWith("omx.allwinner.") ||
                name.startsWith("omx.amlogic.") || name.startsWith("omx.intel.")) return 1;
        // Modern SoC names alone are not evidence of low-latency support.
        return advertisedLowLatency ? 2 : 1;
    }
}
