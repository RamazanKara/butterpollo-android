package com.limelight.binding.video;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import com.limelight.R;

import java.util.ArrayList;
import java.util.Locale;

public final class PerformanceOverlay {
    private static float totalLatencyMs(float networkMs, float decodeMs) {
        return available(networkMs) && available(decodeMs) ? networkMs + decodeMs : -1;
    }

    private static boolean available(float value) {
        return value >= 0 && Float.isFinite(value);
    }

    static String formatCompactStats(Locale locale, float shownFps, float networkMs, float decodeMs,
                                     float frameLossPercent, String lossFormat) {
        ArrayList<String> values = new ArrayList<>();
        if (available(shownFps)) {
            values.add(String.format(locale, "%.1f FPS", shownFps));
        }
        float latencyMs = totalLatencyMs(networkMs, decodeMs);
        if (available(latencyMs)) {
            values.add(String.format(locale, "%.0f ms", latencyMs));
        }
        if (available(frameLossPercent)) {
            values.add(String.format(locale, lossFormat, frameLossPercent));
        }
        return values.isEmpty() ? "" : "● " + String.join(" · ", values);
    }

    static int healthColor(float networkMs, float decodeMs, float frameLossPercent) {
        float latencyMs = totalLatencyMs(networkMs, decodeMs);
        if ((available(latencyMs) && latencyMs >= 60) ||
                (available(frameLossPercent) && frameLossPercent >= 5)) {
            return 0xffff6b6b;
        }
        if (!available(latencyMs) || !available(frameLossPercent) || latencyMs >= 30 || frameLossPercent >= 1) {
            return 0xffffc857;
        }
        return 0xff69db7c;
    }

    @SuppressWarnings("deprecation")
    public static CharSequence compactText(Context context, float shownFps, float networkMs,
                                           float decodeMs, float frameLossPercent) {
        SpannableString text = new SpannableString(formatCompactStats(context.getResources().getConfiguration().locale,
                shownFps, networkMs, decodeMs, frameLossPercent, context.getString(R.string.overlay_compact_loss)));
        if (text.length() != 0) {
            text.setSpan(new ForegroundColorSpan(healthColor(networkMs, decodeMs, frameLossPercent)),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    public static String advancedText(Context context, String video, String network, String decode) {
        return context.getString(R.string.overlay_video) + '\n' + video + "\n\n" +
                context.getString(R.string.overlay_network) + '\n' + network + "\n\n" +
                context.getString(R.string.overlay_decode) + '\n' + decode;
    }
}
