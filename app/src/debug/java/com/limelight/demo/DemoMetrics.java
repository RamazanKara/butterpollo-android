package com.limelight.demo;

import android.content.Context;

import com.limelight.R;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.binding.video.PerformanceOverlay;

import java.util.Locale;

final class DemoMetrics {
    static void update(Context context, PerfOverlayListener listener, long tick) {
        float fps = 119.9f + (float) Math.sin(tick * 0.7) * 0.04f;
        float decode = 6.0f + (float) Math.sin(tick * 0.5) * 0.3f;
        String video = context.getString(R.string.perf_overlay_streamdetails, "1920x1080", 120f) + '\n' +
                context.getString(R.string.perf_overlay_incomingfps, 120f) + '\n' +
                context.getString(R.string.perf_overlay_shownfps, String.format(Locale.ROOT, "%.2f FPS", fps)) +
                "\nAV1 · 10-bit HDR\nUpscaling: FSR 1\nDemo profile · 120 Hz panel";
        String network = context.getString(R.string.perf_overlay_netdrops, 0.1f) + '\n' +
                context.getString(R.string.perf_overlay_netlatency, 8, 1) + '\n' +
                context.getString(R.string.stream_auto_bitrate_status, 40.0 + Math.sin(tick * 0.3));
        String decoder = context.getString(R.string.perf_overlay_decoder, "AV1 (scripted)") + '\n' +
                context.getString(R.string.perf_overlay_dectime, decode) + '\n' +
                context.getString(R.string.perf_overlay_low_latency, context.getString(R.string.perf_overlay_low_latency_android_vendor)) +
                "\nHost encode: 3.1 ms\nQueue: 0.4 ms · render: 1.2 ms\nSample playback: 1080p60 SDR";
        listener.onPerfUpdate(video, network, decoder, PerformanceOverlay.compactText(context, fps, 8, decode, 0.1f));
    }
}
