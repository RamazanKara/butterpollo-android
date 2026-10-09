package com.limelight.preferences;

import android.content.SharedPreferences;

import com.limelight.binding.video.DisplayFrameRatePolicy;

public enum StreamPreset {
    BALANCED("auto", 15000, false, "balanced"),
    LOW_LATENCY(null, 15000, true, "latency"),
    BEST_QUALITY("auto", 40000, false, "balanced"),
    BATTERY_SAVER("auto", 8000, false, "cap-fps");

    public final String codec, pacing;
    public final int bitrate;
    public final boolean vrr;

    StreamPreset(String codec, int bitrate, boolean vrr, String pacing) {
        this.codec = codec;
        this.bitrate = bitrate;
        this.vrr = vrr;
        this.pacing = pacing;
    }

    int fps(float panelMaxHz) {
        return this == BATTERY_SAVER ? 30 : DisplayFrameRatePolicy.streamFrameRate(panelMaxHz);
    }

    void apply(SharedPreferences preferences, float panelMaxHz) {
        SharedPreferences.Editor editor = preferences.edit();
        if (codec != null) {
            editor.putString(PreferenceConfiguration.VIDEO_FORMAT_PREF_STRING, codec);
        }
        editor
                .putInt(PreferenceConfiguration.BITRATE_PREF_STRING, bitrate)
                .putString(PreferenceConfiguration.FPS_PREF_STRING, Integer.toString(fps(panelMaxHz)))
                .putBoolean("checkbox_vrr", vrr)
                .putString(PreferenceConfiguration.FRAME_PACING_PREF_STRING, pacing)
                .putBoolean("checkbox_drop_late_frames", false)
                .putBoolean("checkbox_reduce_refresh_rate", this == BATTERY_SAVER)
                .putBoolean("checkbox_codec_performance", this != BATTERY_SAVER)
                .putBoolean("checkbox_phone_performance_hints", false)
                .apply();
    }
}
