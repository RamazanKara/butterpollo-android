package com.limelight.preferences;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class StreamPresetTest {
    private static final String PHONE = "2400x1080";

    @Test
    public void nativeUsesThePanelAndTurnsEveryLatencySwitchOn() {
        Map<String, Object> values = new HashMap<>();
        for (String key : StreamPreset.LATENCY_SWITCHES) values.put(key, false);
        values.put("checkbox_vrr", true);
        values.put("upscaling_mode", "fsr1");
        values.put("frame_pacing", "balanced");
        int[] applies = {0};
        StreamPreset.NATIVE.apply(preferences(values, applies), PHONE, 120);
        assertEquals(1, applies[0]);
        assertEquals(PHONE, values.get("list_resolution"));
        assertEquals("120", values.get("list_fps"));
        assertEquals(PreferenceConfiguration.getDefaultBitrate(PHONE, "120"), values.get("seekbar_bitrate_kbps"));
        assertEquals(false, values.get("checkbox_vrr"));
        assertEquals("off", values.get("upscaling_mode"));
        assertEquals("latency", values.get("frame_pacing"));
        assertEquals(false, values.get("checkbox_reduce_refresh_rate"));
        assertEquals(true, values.get("checkbox_codec_performance"));
        for (String key : StreamPreset.LATENCY_SWITCHES) assertEquals(key, true, values.get(key));
    }

    @Test
    public void frontBufferStaysOptInBecauseItTears() {
        assertFalse(java.util.Arrays.asList(StreamPreset.LATENCY_SWITCHES)
                .contains(PreferenceConfiguration.PYROWAVE_FRONT_BUFFER_PREF_STRING));
        Map<String, Object> values = new HashMap<>();
        values.put(PreferenceConfiguration.PYROWAVE_FRONT_BUFFER_PREF_STRING, false);
        StreamPreset.NATIVE.apply(preferences(values, new int[1]), PHONE, 120);
        assertEquals(false, values.get(PreferenceConfiguration.PYROWAVE_FRONT_BUFFER_PREF_STRING));
    }

    @Test
    public void batterySaverKeepsNativeResolutionAt30Fps() {
        Map<String, Object> values = new HashMap<>();
        StreamPreset.BATTERY_SAVER.apply(preferences(values, new int[1]), PHONE, 144);
        assertEquals(PHONE, values.get("list_resolution"));
        assertEquals("30", values.get("list_fps"));
        assertEquals("cap-fps", values.get("frame_pacing"));
        assertEquals(true, values.get("checkbox_reduce_refresh_rate"));
        assertEquals(false, values.get("checkbox_codec_performance"));
        assertEquals(false, values.get(PreferenceConfiguration.GPU_MAX_CLOCKS_PREF_STRING));
    }

    @Test
    public void presetsLeaveCodecHdrAnd444Alone() {
        for (StreamPreset preset : StreamPreset.values()) {
            for (String codec : new String[] {"auto", "forceav1", "forceh265", "neverh265", "forcepyrowave"}) {
                Map<String, Object> values = new HashMap<>();
                values.put("video_format", codec);
                values.put("checkbox_enable_hdr", true);
                values.put("checkbox_yuv444", true);
                preset.apply(preferences(values, new int[1]), PHONE, 120);
                assertEquals(codec, values.get("video_format"));
                assertEquals(true, values.get("checkbox_enable_hdr"));
                assertEquals(true, values.get("checkbox_yuv444"));
            }
        }
    }

    @Test
    public void presetsFollowPanelRateWithinHostLimitExceptBatterySaver() {
        for (StreamPreset preset : StreamPreset.values()) {
            for (float rate : new float[] {59.94f, 90, 119.88f, 144, 165, 240, 1200}) {
                Map<String, Object> values = new HashMap<>();
                preset.apply(preferences(values, new int[1]), PHONE, rate);
                int expected = preset == StreamPreset.BATTERY_SAVER ? 30 : Math.min(1000, Math.round(rate));
                assertEquals(Integer.toString(expected), values.get("list_fps"));
            }
        }
    }

    @Test
    public void matchingReflectsValuesRatherThanLastSelectedPreset() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences preferences = preferences(values, new int[1]);
        for (StreamPreset selected : StreamPreset.values()) {
            selected.apply(preferences, PHONE, 120);
            for (StreamPreset candidate : StreamPreset.values()) {
                assertEquals(selected == candidate, candidate.matches(preferences, PHONE, 120));
            }
            assertFalse(selected.matches(preferences, "1920x1080", 120));
            values.put("seekbar_bitrate_kbps", (Integer) values.get("seekbar_bitrate_kbps") + 500);
            assertFalse(selected.matches(preferences, PHONE, 120));
            selected.apply(preferences, PHONE, 120);
            values.put("upscaling_mode", "sgsr1");
            assertFalse(selected.matches(preferences, PHONE, 120));
        }
    }

    @Test
    public void turningOffALatencySwitchMakesNativeCustom() {
        for (String key : StreamPreset.LATENCY_SWITCHES) {
            Map<String, Object> values = new HashMap<>();
            SharedPreferences preferences = preferences(values, new int[1]);
            StreamPreset.NATIVE.apply(preferences, PHONE, 120);
            values.put(key, false);
            assertFalse(key, StreamPreset.NATIVE.matches(preferences, PHONE, 120));
        }
    }

    @Test
    public void freshDefaultsMatchNative() {
        // Values never written fall back to their defaults, which are Native's.
        Map<String, Object> values = new HashMap<>();
        values.put("list_resolution", PHONE);
        values.put("list_fps", "120");
        assertTrue(StreamPreset.NATIVE.matches(preferences(values, new int[1]), PHONE, 120));
    }

    @Test
    public void migrationMovesOldDefaultsToNativeOnce() {
        Map<String, Object> values = new HashMap<>();
        // What PcView stored on first launch before this change.
        values.put("list_resolution", "1280x720");
        values.put("list_fps", "60");
        values.put("seekbar_bitrate_kbps", PreferenceConfiguration.getDefaultBitrate("1280x720", "60"));
        values.put("frame_pacing", "balanced");
        values.put("video_format", "forceav1");
        values.put("checkbox_enable_hdr", true);
        values.put("checkbox_vrr", true);
        for (String key : StreamPreset.LATENCY_SWITCHES) values.put(key, false);
        SharedPreferences preferences = preferences(values, new int[1]);

        assertTrue(PreferenceConfiguration.migrateToNativeDefaults(preferences, PHONE, 120));
        assertEquals(PHONE, values.get("list_resolution"));
        assertEquals("120", values.get("list_fps"));
        assertEquals(PreferenceConfiguration.getDefaultBitrate(PHONE, "120"), values.get("seekbar_bitrate_kbps"));
        assertEquals("latency", values.get("frame_pacing"));
        for (String key : StreamPreset.LATENCY_SWITCHES) assertEquals(key, true, values.get(key));
        assertEquals("forceav1", values.get("video_format"));
        assertEquals(true, values.get("checkbox_enable_hdr"));
        assertEquals(true, values.get("checkbox_vrr"));
        assertEquals(PreferenceConfiguration.NATIVE_DEFAULTS_VERSION, values.get("prefs_version"));

        // Later changes stick.
        values.put("list_resolution", "1920x1080");
        values.put("checkbox_unbatched_input", false);
        assertFalse(PreferenceConfiguration.migrateToNativeDefaults(preferences, PHONE, 120));
        assertEquals("1920x1080", values.get("list_resolution"));
        assertEquals(false, values.get("checkbox_unbatched_input"));
    }

    @Test
    public void migrationWaitsWhenTheScreenIsUnknown() {
        Map<String, Object> values = new HashMap<>();
        values.put("list_resolution", "1280x720");
        SharedPreferences preferences = preferences(values, new int[1]);
        assertFalse(PreferenceConfiguration.migrateToNativeDefaults(preferences, null, 60));
        assertEquals("1280x720", values.get("list_resolution"));
        assertFalse(values.containsKey("prefs_version"));
    }

    private SharedPreferences preferences(Map<String, Object> values, int[] applies) {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) {
                        values.put((String) args[0], args[1]);
                        return proxy;
                    }
                    if (method.getName().equals("remove")) {
                        values.remove((String) args[0]);
                        return proxy;
                    }
                    if (method.getName().equals("apply")) {
                        applies[0]++;
                    }
                    return null;
                });
        return (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("get")) {
                        return values.getOrDefault(args[0], args[1]);
                    }
                    if (method.getName().equals("contains")) {
                        return values.containsKey(args[0]);
                    }
                    return editor;
                });
    }
}
