package com.limelight.binding.video;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Vendor decoder keys that cut decode latency, matched against what a codec actually exposes.
 * Android 12+ lists a codec's vendor parameters, so only keys the decoder declares are sent.
 */
final class VendorLowLatencyKeys {
    // Known keys and the value that enables the low-latency behaviour.
    static final Map<String, Integer> KNOWN;

    static {
        Map<String, Integer> known = new LinkedHashMap<>();
        // Qualcomm (OMX and Codec2): low-latency mode, decode-order output.
        known.put("vendor.qti-ext-dec-low-latency.enable", 1);
        known.put("vendor.qti-ext-dec-picture-order.enable", 1);
        // Snapdragon 8 Gen 2 and newer hold output behind a fence; a software fence releases it sooner.
        known.put("vendor.qti-ext-output-sw-fence-enable.value", 1);
        known.put("vendor.qti-ext-output-fence.enable", 1);
        known.put("vendor.qti-ext-output-fence.fence_type", 1);
        // Samsung Exynos and Google Tensor.
        known.put("vendor.rtc-ext-dec-low-latency.enable", 1);
        // HiSilicon Kirin.
        known.put("vendor.hisi-ext-low-latency-video-dec.video-scene-for-low-latency-req", 1);
        // Amlogic and other generic vendor stacks.
        known.put("vendor.low-latency.enable", 1);
        // MediaTek Codec2.
        known.put("vendor.mtk.vdec.low-latency.mode", 1);
        known.put("vendor.mtk.vdec.low-latency.mode.value", 1);
        known.put("vendor.mtk.vdec.cpu.boost.mode", 1);
        known.put("vendor.mtk.vdec.cpu.boost.mode.value", 1);
        // NVIDIA Tegra.
        known.put("vendor.nvidia.disable-output-reorder", 1);
        known.put("vendor.nvidia.disable-output-reorder.value", 1);
        KNOWN = Collections.unmodifiableMap(known);
    }

    private VendorLowLatencyKeys() {}

    /**
     * Keys to set for a decoder exposing these integer vendor parameters: every known key it
     * declares, plus any other integer switch whose name says it enables low latency.
     */
    static Map<String, Integer> forSupported(Collection<String> integerParams) {
        Map<String, Integer> keys = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> known : KNOWN.entrySet()) {
            if (containsIgnoreCase(integerParams, known.getKey())) {
                keys.put(known.getKey(), known.getValue());
            }
        }
        for (String param : integerParams) {
            if (!containsIgnoreCase(keys.keySet(), param) && looksLikeLowLatencySwitch(param)) {
                keys.put(param, 1);
            }
        }
        return keys;
    }

    static boolean isKnown(String key) {
        return containsIgnoreCase(KNOWN.keySet(), key);
    }

    /** Keys that only switch the low-latency mode itself, kept on the last fallback try. */
    static boolean isCoreSwitch(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("low-latency") || lower.contains("lowlatency") || lower.contains("low_latency");
    }

    static boolean looksLikeLowLatencySwitch(String param) {
        String lower = param.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("vendor.") || !isCoreSwitch(lower)) {
            return false;
        }
        // Skip inverted, status and ultra modes; ultra-low-latency trades stability for little gain.
        if (lower.contains("disable") || lower.contains("rdy") || lower.contains("ultra")
                || lower.contains("status") || lower.contains("support")) {
            return false;
        }
        return lower.endsWith(".enable") || lower.endsWith(".value") || lower.endsWith(".mode")
                || lower.endsWith(".req") || lower.endsWith("-req");
    }

    private static boolean containsIgnoreCase(Collection<String> values, String wanted) {
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }
}
