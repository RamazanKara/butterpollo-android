package com.limelight.preferences;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class StreamPresetTest {
    @Test
    public void presetsHaveConsistentStreamValues() {
        assertPreset(StreamPreset.BALANCED, "auto", 15000, 120, false, "balanced");
        assertPreset(StreamPreset.LOW_LATENCY, null, 15000, 120, true, "latency");
        assertPreset(StreamPreset.BEST_QUALITY, "auto", 40000, 120, false, "balanced");
        assertPreset(StreamPreset.BATTERY_SAVER, "auto", 8000, 30, false, "cap-fps");
    }

    private void assertPreset(StreamPreset preset, String codec, int bitrate, int fps, boolean vrr, String pacing) {
        assertEquals(codec, preset.codec);
        assertEquals(bitrate, preset.bitrate);
        assertEquals(fps, preset.fps(120));
        assertEquals(vrr, preset.vrr);
        assertEquals(pacing, preset.pacing);
    }

    @Test
    public void switchingPresetsOverwritesConflictingValuesInOneEdit() {
        Map<String, Object> values = new HashMap<>();
        values.put("list_resolution", "1920x1080");
        values.put("checkbox_enable_hdr", true);
        int[] applies = {0};
        SharedPreferences preferences = preferences(values, applies);
        String[] upscalers = {"off", "sgsr1", "fsr1", "bilinear"};
        for (StreamPreset preset : StreamPreset.values()) {
            values.put("video_format", "forceav1");
            values.put("checkbox_drop_late_frames", true);
            values.put("upscaling_mode", "bilinear");
            preset.apply(preferences, 120);
            assertEquals(preset == StreamPreset.LOW_LATENCY ? "forceav1" : preset.codec, values.get("video_format"));
            assertEquals(preset.bitrate, values.get("seekbar_bitrate_kbps"));
            assertEquals(upscalers[preset.ordinal()], values.get("upscaling_mode"));
            assertEquals(preset == StreamPreset.BATTERY_SAVER ? "30" : "120", values.get("list_fps"));
            assertEquals(preset.vrr, values.get("checkbox_vrr"));
            assertEquals(preset.pacing, values.get("frame_pacing"));
            assertEquals(false, values.get("checkbox_drop_late_frames"));
            assertEquals(preset == StreamPreset.BATTERY_SAVER, values.get("checkbox_reduce_refresh_rate"));
            assertEquals(preset != StreamPreset.BATTERY_SAVER, values.get("checkbox_codec_performance"));
            assertEquals(false, values.get("checkbox_phone_performance_hints"));
        }
        assertEquals(4, applies[0]);
        assertEquals("1920x1080", values.get("list_resolution"));
        assertEquals(true, values.get("checkbox_enable_hdr"));
    }

    @Test
    public void lowLatencyPreservesEveryCodecIncludingDefaultAuto() {
        for (String codec : new String[] {"auto", "forceav1", "forceh265", "neverh265", "forcepyrowave", null}) {
            Map<String, Object> values = new HashMap<>();
            if (codec != null) values.put("video_format", codec);
            StreamPreset.LOW_LATENCY.apply(preferences(values, new int[1]), 144);
            assertEquals(codec, values.get("video_format"));
            assertEquals(codec != null, values.containsKey("video_format"));
            assertEquals("144", values.get("list_fps"));
        }
    }

    @Test
    public void presetsFollowPanelRateWithinHostLimitExceptBatterySaver() {
        for (StreamPreset preset : StreamPreset.values()) {
            for (float rate : new float[] {59.94f, 90, 119.88f, 144, 165, 240, 1200}) {
                Map<String, Object> values = new HashMap<>();
                preset.apply(preferences(values, new int[1]), rate);
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
            selected.apply(preferences, 120);
            for (StreamPreset candidate : StreamPreset.values()) {
                assertEquals(selected == candidate, candidate.matches(preferences, 120));
            }
            values.put("seekbar_bitrate_kbps", selected.bitrate + 500);
            assertFalse(selected.matches(preferences, 120));
            selected.apply(preferences, 120);
            values.put("checkbox_phone_performance_hints", true);
            assertFalse(selected.matches(preferences, 120));
        }
    }

    @Test
    public void lowLatencyMatchingHonoursItsPreservedCodec() {
        Map<String, Object> values = new HashMap<>();
        values.put("video_format", "forceav1");
        SharedPreferences preferences = preferences(values, new int[1]);
        StreamPreset.LOW_LATENCY.apply(preferences, 144);
        assertTrue(StreamPreset.LOW_LATENCY.matches(preferences, 144));
        assertFalse(StreamPreset.LOW_LATENCY.matches(preferences, 120));
    }

    @Test
    public void changingUpscalingMakesThePresetCustom() {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences preferences = preferences(values, new int[1]);
        for (StreamPreset preset : StreamPreset.values()) {
            preset.apply(preferences, 120);
            Object selectedMode = values.get("upscaling_mode");
            for (String mode : new String[] {"off", "bilinear", "fsr1", "sgsr1"}) {
                values.put("upscaling_mode", mode);
                assertEquals(mode.equals(selectedMode), preset.matches(preferences, 120));
            }
        }
    }

    @Test
    public void freshSettingsAreUntouchedUntilAPresetOrStreamValueIsSet() {
        Map<String, Object> values = new HashMap<>();
        values.put("list_resolution", "1920x1080");
        SharedPreferences preferences = preferences(values, new int[1]);
        assertTrue(StreamPreset.isUntouched(preferences));
        // PcView writes the XML defaults on first launch; stored defaults still count as untouched.
        values.put("video_format", "auto");
        values.put("list_fps", "60");
        values.put("frame_pacing", "latency");
        values.put("upscaling_mode", "off");
        values.put("checkbox_vrr", false);
        values.put("checkbox_codec_performance", true);
        values.put("seekbar_bitrate_kbps", PreferenceConfiguration.getDefaultBitrate("1920x1080", "60"));
        assertTrue(StreamPreset.isUntouched(preferences));
        StreamPreset.BALANCED.apply(preferences, 120);
        assertFalse(StreamPreset.isUntouched(preferences));
        values.clear();
        values.put("seekbar_bitrate_kbps", 20000);
        assertFalse(StreamPreset.isUntouched(preferences));
        values.clear();
        values.put("checkbox_vrr", true);
        assertFalse(StreamPreset.isUntouched(preferences));
    }

    private SharedPreferences preferences(Map<String, Object> values, int[] applies) {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("put")) {
                        values.put((String) args[0], args[1]);
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
