package com.limelight.demo;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.binding.video.PerformanceOverlay;
import com.limelight.ui.ActionSheet;
import com.limelight.ui.StreamView;

import java.io.IOException;

public final class DemoGameActivity extends Activity implements SurfaceHolder.Callback, PerfOverlayListener {
    static volatile boolean forceH264;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StreamView stream;
    private TextView overlay, label;
    private VirtualController controls;
    private MediaPlayer player;
    private boolean compact, showControls, prepared, started, fallback;
    private int position;
    private long tick;
    private ActionSheet menu;
    private final Runnable metrics = new Runnable() {
        @Override public void run() {
            DemoMetrics.update(DemoGameActivity.this, DemoGameActivity.this, tick++);
            handler.postDelayed(this, 2000);
        }
    };

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new DemoContext(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        // The sample has no text input; keep the soft keyboard (and its navigation-bar buttons) off the stream.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_game);
        compact = state == null ? !"advanced".equals(getIntent().getStringExtra("state")) : state.getBoolean("compact");
        showControls = state == null ? "touch".equals(getIntent().getStringExtra("state")) : state.getBoolean("controls");
        position = state == null ? 0 : state.getInt("position");
        stream = findViewById(R.id.surfaceView);
        stream.setDesiredAspectRatio(16.0 / 9.0);
        stream.initializeSurface(false);
        stream.getHolder().addCallback(this);
        overlay = findViewById(R.id.performanceOverlay);
        findViewById(R.id.performanceOverlayScroll).setVisibility(View.VISIBLE);
        placeOverlay();
        overlay.setOnLongClickListener(view -> {
            setCompact(!compact);
            return true;
        });
        findViewById(R.id.backgroundTouchView).setOnClickListener(view -> showMenu());
        stream.setOnClickListener(view -> showMenu());
        FrameLayout container = (FrameLayout) stream.getParent();
        controls = new VirtualController(null, container, this);
        controls.refreshLayout();
        controls.setOpacity(65);
        if (showControls) controls.show(); else controls.hide();
        label = new TextView(this);
        label.setText(R.string.demo_sample);
        label.setTextColor(0xffbcc8ce);
        label.setTextSize(11);
        label.setPadding(8, 8, 8, 12);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        container.addView(label, params);
        DemoMetrics.update(this, this, 0);
    }

    private void setCompact(boolean value) {
        compact = value;
        placeOverlay();
        findViewById(R.id.performanceOverlayScroll).scrollTo(0, 0);
        DemoMetrics.update(this, this, tick);
    }

    /**
     * The sample's own HUD (title panel top left, speed panel top right, shield bottom left) fills the corners of the
     * landscape picture, so the overlay sits where the picture has no text: top centre for the one-line compact pill, and
     * the vertical middle of the left edge for the full advanced list. In portrait the picture is a strip, and the
     * corner over the black bar above it is free.
     */
    private void placeOverlay() {
        View scroll = findViewById(R.id.performanceOverlayScroll);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) scroll.getLayoutParams();
        params.gravity = !landscape ? Gravity.TOP | Gravity.START
                : compact ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL | Gravity.START;
        scroll.setLayoutParams(params);
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, !landscape ? 12 : compact ? 14 : 10);
    }

    @Override public void onPerfUpdate(String video, String network, String decode, CharSequence compactText) {
        overlay.setMaxLines(compact ? 1 : Integer.MAX_VALUE);
        overlay.setEllipsize(compact ? TextUtils.TruncateAt.END : null);
        overlay.setText(compact ? compactText : PerformanceOverlay.advancedText(this, video, network, decode));
    }

    private void showMenu() {
        if (menu != null) return;
        menu = new ActionSheet(this, getString(R.string.stream_overlay_menu));
        menu.addAction(R.drawable.ic_remote_monitor, getString(R.string.overlay_compact))
                .setOnClickListener(view -> { setCompact(true); menu.dismiss(); });
        menu.addAction(R.drawable.ic_remote_monitor, getString(R.string.overlay_advanced))
                .setOnClickListener(view -> { setCompact(false); menu.dismiss(); });
        menu.addAction(R.drawable.ic_settings, getString(showControls ? R.string.stream_controls_hide : R.string.stream_controls_show))
                .setOnClickListener(view -> {
                    showControls = !showControls;
                    if (showControls) controls.show(); else controls.hide();
                    menu.dismiss();
                });
        menu.setOnDismissListener(dialog -> menu = null);
        menu.show();
    }

    @Override public void onBackPressed() {
        if (menu != null) menu.dismiss(); else super.onBackPressed();
    }

    private String sample() {
        // Launching the demo with "--es codec h264" forces the 8-bit sample for this process; software
        // AV1/HEVC 10-bit playback renders corrupt on some emulators.
        if (!fallback && !forceH264) {
            for (String[] candidate : new String[][] {{"video/av01", "av1"}, {"video/hevc", "hevc"}}) {
                for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                    if (info.isEncoder()) continue;
                    for (String type : info.getSupportedTypes()) {
                        if (candidate[0].equalsIgnoreCase(type)) {
                            try (AssetFileDescriptor ignored = getAssets().openFd("demo/stream-" + candidate[1] + ".mp4")) {
                                return candidate[1];
                            } catch (IOException ignored) { }
                        }
                    }
                }
            }
        }
        return "h264";
    }

    private void play(SurfaceHolder holder) {
        String codec = sample();
        player = new MediaPlayer();
        player.setDisplay(holder);
        player.setLooping(true);
        player.setVolume(0, 0);
        player.setOnPreparedListener(media -> {
            prepared = true;
            media.seekTo(position);
            if (started) media.start();
        });
        player.setOnErrorListener((media, what, extra) -> {
            playbackFailed(holder, codec);
            return true;
        });
        try (AssetFileDescriptor asset = getAssets().openFd("demo/stream-" + codec + ".mp4")) {
            player.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
            player.prepareAsync();
        } catch (IOException e) {
            playbackFailed(holder, codec);
        }
    }

    private void playbackFailed(SurfaceHolder holder, String codec) {
        releasePlayer();
        if (!"h264".equals(codec)) {
            fallback = true;
            play(holder);
        } else {
            label.setText(R.string.demo_playback_error);
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { play(holder); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (prepared) position = player.getCurrentPosition();
        releasePlayer();
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        handler.post(metrics);
        if (prepared) player.start();
    }

    @Override protected void onStop() {
        started = false;
        handler.removeCallbacksAndMessages(null);
        if (prepared) player.pause();
        controls.releaseInput();
        if (menu != null) menu.dismiss();
        super.onStop();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        controls.hide();
        releasePlayer();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("compact", compact);
        state.putBoolean("controls", showControls);
        state.putInt("position", prepared ? player.getCurrentPosition() : position);
        super.onSaveInstanceState(state);
    }

    @Override public void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterPip();
    }

    @Override public boolean onPictureInPictureRequested() { return enterPip(); }

    private boolean enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !prepared ||
                !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false;
        if (isInPictureInPictureMode()) return true;
        Rect rect = new Rect();
        stream.getGlobalVisibleRect(rect);
        return enterPictureInPictureMode(new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9)).setSourceRectHint(rect).build());
    }

    @Override public void onPictureInPictureModeChanged(boolean pip, Configuration configuration) {
        super.onPictureInPictureModeChanged(pip, configuration);
        findViewById(R.id.performanceOverlayScroll).setVisibility(pip ? View.GONE : View.VISIBLE);
        label.setVisibility(pip ? View.GONE : View.VISIBLE);
        if (pip || !showControls) controls.hide(); else controls.show();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        placeOverlay();
        controls.refreshLayout();
        if (showControls && (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode())) controls.show();
        else controls.hide();
    }
}
