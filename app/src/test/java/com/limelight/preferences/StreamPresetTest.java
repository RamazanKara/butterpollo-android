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
        assertPreset(StreamPreset.BALANCED, "auto", 15000, 60, false, "balanced");
        assertPreset(StreamPreset.LOW_LATENCY, "neverh265", 15000, 60, true, "latency");
        assertPreset(StreamPreset.BEST_QUALITY, "auto", 40000, 60, false, "balanced");
        assertPreset(StreamPreset.BATTERY_SAVER, "auto", 8000, 30, false, "cap-fps");
    }

    private void assertPreset(StreamPreset preset, String codec, int bitrate, int fps, boolean vrr, String pacing) {
        assertEquals(codec, preset.codec);
        assertEquals(bitrate, preset.bitrate);
        assertEquals(fps, preset.fps);
        assertEquals(vrr, preset.vrr);
        assertEquals(pacing, preset.pacing);
    }

    @Test
    public void switchingPresetsOverwritesConflictingValuesInOneEdit() {
        Map<String, Object> values = new HashMap<>();
        values.put("list_resolution", "1920x1080");
        values.put("checkbox_enable_hdr", true);
        int[] applies = {0};
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
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> editor);
        for (StreamPreset preset : StreamPreset.values()) {
            values.put("checkbox_drop_late_frames", true);
            preset.apply(preferences);
            assertEquals(preset.codec, values.get("video_format"));
            assertEquals(preset.bitrate, values.get("seekbar_bitrate_kbps"));
            assertEquals(Integer.toString(preset.fps), values.get("list_fps"));
            assertEquals(preset.vrr, values.get("checkbox_vrr"));
            assertEquals(preset.pacing, values.get("frame_pacing"));
            assertEquals(false, values.get("checkbox_drop_late_frames"));
            assertEquals(preset == StreamPreset.BATTERY_SAVER, values.get("checkbox_reduce_refresh_rate"));
            assertEquals(preset != StreamPreset.BATTERY_SAVER, values.get("checkbox_codec_performance"));
            assertEquals(preset != StreamPreset.BATTERY_SAVER, values.get("checkbox_phone_performance_hints"));
        }
        assertEquals(4, applies[0]);
        assertEquals("1920x1080", values.get("list_resolution"));
        assertEquals(true, values.get("checkbox_enable_hdr"));
    }
}
