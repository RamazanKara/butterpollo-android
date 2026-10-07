package com.limelight;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.ui.StreamView;

/** Debug-only rendering check using the stream layout and production latency formatter. */
public class LatencyOverlaySmokeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_game);
        StreamView stream = findViewById(R.id.surfaceView);
        stream.initializeSurface(false);
        TextView overlay = findViewById(R.id.performanceOverlay);
        overlay.setText(getString(R.string.smoke_overlay_message,
                MediaCodecDecoderRenderer.formatLatencyOverlay(this, new double[5][4])));
        overlay.setVisibility(View.VISIBLE);
    }
}
