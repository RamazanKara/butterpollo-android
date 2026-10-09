package com.limelight;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.video.UpscalingPolicy;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.StreamView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk = 29, application = Application.class)
public class GameLifecycleTest {
    @Test
    @Config(qualifiers = "sw600dp-w1000dp-h800dp")
    public void largeWindowsDoNotForceTheDisplayIntoLandscape() {
        Game activity = Robolectric.buildActivity(Game.class).get();
        ReflectionHelpers.callInstanceMethod(activity, "setPreferredOrientationForCurrentDisplay");
        assertEquals(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER, activity.getRequestedOrientation());
    }

    @Test
    public void offUsesStreamSizedCompositorBuffersWithoutLosingLetterboxing() {
        Game activity = Robolectric.buildActivity(Game.class).get();
        for (UpscalingPolicy.Mode mode : UpscalingPolicy.Mode.values()) {
            for (boolean stretch : new boolean[] {false, true}) {
                PreferenceConfiguration prefs = new PreferenceConfiguration();
                prefs.width = 1280;
                prefs.height = 720;
                prefs.fps = 60;
                prefs.upscalingMode = mode;
                prefs.stretchVideo = stretch;
                int[] bufferSize = new int[2];
                SurfaceHolder holder = new org.robolectric.shadows.ShadowSurfaceView.FakeSurfaceHolder() {
                    @Override public void setFixedSize(int width, int height) {
                        bufferSize[0] = width;
                        bufferSize[1] = height;
                    }
                };
                StreamView view = new StreamView(activity) {
                    @Override public SurfaceHolder getHolder() { return holder; }
                };
                ReflectionHelpers.setField(activity, "prefConfig", prefs);
                ReflectionHelpers.setField(activity, "streamView", view);
                ReflectionHelpers.callInstanceMethod(activity, "prepareDisplayForRendering");
                assertArrayEquals(mode == UpscalingPolicy.Mode.OFF ? new int[] {1280, 720} : new int[2],
                        bufferSize);
                view.measure(View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
                assertEquals(stretch ? 2400 : 1920, view.getMeasuredWidth());
                assertEquals(1080, view.getMeasuredHeight());
            }
        }
    }

    @Test
    public void aNewLaunchIntentReplacesTheExistingSingleTaskStream() {
        ActivityController<Game> controller = Robolectric.buildActivity(Game.class).create();
        Intent nextStream = new Intent(controller.get(), Game.class)
                .putExtra(Game.EXTRA_HOST, "192.0.2.2")
                .putExtra(Game.EXTRA_APP_ID, 42);
        try {
            controller.newIntent(nextStream);
            Intent launched = org.robolectric.Shadows.shadowOf(controller.get()).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals("192.0.2.2", launched.getStringExtra(Game.EXTRA_HOST));
            assertEquals(42, launched.getIntExtra(Game.EXTRA_APP_ID, 0));
            assertTrue(controller.get().isFinishing());
        } finally {
            controller.destroy();
        }
    }

    @Test
    public void rootKeyboardCallbacksUseTheMainThread() throws Exception {
        Game activity = Robolectric.buildActivity(Game.class).get();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        ReflectionHelpers.setField(activity, "keyboardTranslator", new KeyboardTranslator() {
            @Override public short translate(int keycode, int deviceId) {
                callbackThread.set(Thread.currentThread());
                return 0;
            }
        });
        Thread inputThread = new Thread(() -> activity.keyboardEvent(true, (short) 29));
        inputThread.start();
        inputThread.join(2000);
        assertFalse(inputThread.isAlive());
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertSame(Looper.getMainLooper().getThread(), callbackThread.get());
    }

    @Test
    public void stoppingAStreamReleasesPowerLocksBeforeTheErrorDialogIsClosed() {
        ActivityController<Game> controller = Robolectric.buildActivity(Game.class).create();
        Game activity = controller.get();
        WifiManager wifi = (WifiManager) activity.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.WifiLock highPerf = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "test-high");
        WifiManager.WifiLock lowLatency = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "test-low");
        highPerf.acquire();
        lowLatency.acquire();
        ReflectionHelpers.setField(activity, "highPerfWifiLock", highPerf);
        ReflectionHelpers.setField(activity, "lowLatencyWifiLock", lowLatency);
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            ReflectionHelpers.callInstanceMethod(activity, "stopConnection");
            assertFalse(highPerf.isHeld());
            assertFalse(lowLatency.isHeld());
            assertEquals(0, activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            ReflectionHelpers.callInstanceMethod(activity, "stopConnection");
        } finally {
            controller.destroy();
        }
    }
}
