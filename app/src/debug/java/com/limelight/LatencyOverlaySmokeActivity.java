package com.limelight;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.PerformanceOverlay;
import com.limelight.ui.StreamView;

/** Debug-only rendering check using the stream layout and production overlay formatters. */
public class LatencyOverlaySmokeActivity extends Activity {
    private boolean compact = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_game);
        StreamView stream = findViewById(R.id.surfaceView);
        stream.initializeSurface(false);
        if (savedInstanceState != null) compact = savedInstanceState.getBoolean("compact", true);
        TextView message = findViewById(R.id.inputOnlyStatus);
        message.setText(R.string.smoke_overlay_message);
        message.setVisibility(View.VISIBLE);
        findViewById(R.id.performanceOverlayScroll).setVisibility(View.VISIBLE);
        findViewById(R.id.performanceOverlay).setOnLongClickListener(view -> {
            compact = !compact;
            findViewById(R.id.performanceOverlayScroll).scrollTo(0, 0);
            updateOverlay();
            return true;
        });
        updateOverlay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("compact", compact);
        super.onSaveInstanceState(outState);
    }

    private void updateOverlay() {
        TextView overlay = findViewById(R.id.performanceOverlay);
        overlay.setMaxLines(compact ? 1 : Integer.MAX_VALUE);
        overlay.setEllipsize(compact ? android.text.TextUtils.TruncateAt.END : null);
        if (compact) {
            overlay.setText(PerformanceOverlay.compactText(this, 119.94f, 8, 4.6f, 0.14f));
            return;
        }
        String video = getString(R.string.perf_overlay_streamdetails, "1280x720", 120f) + '\n' +
                getString(R.string.perf_overlay_incomingfps, 120f) + '\n' +
                getString(R.string.perf_overlay_releasedfps, 120f) + '\n' +
                getString(R.string.perf_overlay_shownfps, "119.94 FPS") + '\n' +
                getString(R.string.perf_overlay_presentdrops, 0) + "\nYUV 4:2:0 10-bit\n" +
                "Panel: 120.00 Hz · render: 120.00 Hz · VRR: max Hz";
        String network = getString(R.string.perf_overlay_netdrops, 0.14f) + '\n' +
                getString(R.string.perf_overlay_netlatency, 8, 1) + '\n' +
                getString(R.string.stream_auto_bitrate_status, 15.0);
        String decode = getString(R.string.perf_overlay_decoder, "c2.qti.av1.decoder (video/av01)") + '\n' +
                getString(R.string.perf_overlay_low_latency, getString(R.string.perf_overlay_low_latency_android_vendor)) + '\n' +
                getString(R.string.perf_overlay_low_latency_keys, "low-latency=1") + '\n' +
                getString(R.string.perf_overlay_low_latency_unconfirmed, "none") + '\n' +
                getString(R.string.perf_overlay_phone_hints, getString(R.string.stream_disabled)) + '\n' +
                getString(R.string.perf_overlay_dectime, 4.6f) + '\n' +
                MediaCodecDecoderRenderer.formatLatencyOverlay(this, new double[][] {
                        {600, 0.4, 0.8, 1.2}, {600, 4.6, 5.8, 6.4}, {600, 1.2, 1.8, 2.5},
                        {600, 6.4, 8.4, 10.1}, {600, 2.1, 2.8, 3.2}
                });
        overlay.setText(PerformanceOverlay.advancedText(this, video, network, decode));
    }
}
