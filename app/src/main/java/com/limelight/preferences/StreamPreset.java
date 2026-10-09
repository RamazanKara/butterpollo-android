package com.limelight.preferences;

import android.content.SharedPreferences;

public enum StreamPreset {
    BALANCED("auto", 15000, 60, false, "balanced"),
    LOW_LATENCY("neverh265", 15000, 60, true, "latency"),
    BEST_QUALITY("auto", 40000, 60, false, "balanced"),
    BATTERY_SAVER("auto", 8000, 30, false, "cap-fps");

    public final String codec, pacing;
    public final int bitrate, fps;
    public final boolean vrr;

    StreamPreset(String codec, int bitrate, int fps, boolean vrr, String pacing) {
        this.codec = codec;
        this.bitrate = bitrate;
        this.fps = fps;
        this.vrr = vrr;
        this.pacing = pacing;
    }

    void apply(SharedPreferences preferences) {
        preferences.edit()
                .putString(PreferenceConfiguration.VIDEO_FORMAT_PREF_STRING, codec)
                .putInt(PreferenceConfiguration.BITRATE_PREF_STRING, bitrate)
                .putString(PreferenceConfiguration.FPS_PREF_STRING, Integer.toString(fps))
                .putBoolean("checkbox_vrr", vrr)
                .putString(PreferenceConfiguration.FRAME_PACING_PREF_STRING, pacing)
                .putBoolean("checkbox_drop_late_frames", false)
                .putBoolean("checkbox_reduce_refresh_rate", this == BATTERY_SAVER)
                .putBoolean("checkbox_codec_performance", this != BATTERY_SAVER)
                .putBoolean("checkbox_phone_performance_hints", this != BATTERY_SAVER)
                .apply();
    }
}
