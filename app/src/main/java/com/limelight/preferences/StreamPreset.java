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

    boolean matches(SharedPreferences preferences, float panelMaxHz) {
        return (codec == null || codec.equals(preferences.getString("video_format", "auto"))) &&
                preferences.getInt("seekbar_bitrate_kbps", PreferenceConfiguration.getDefaultBitrate(
                        preferences.getString("list_resolution", "1280x720"),
                        preferences.getString("list_fps", "60"))) == bitrate &&
                Integer.toString(fps(panelMaxHz)).equals(preferences.getString("list_fps", "60")) &&
                preferences.getBoolean("checkbox_vrr", false) == vrr &&
                pacing.equals(preferences.getString("frame_pacing", "latency")) &&
                (this == BEST_QUALITY ? "fsr1" : "off").equals(preferences.getString(
                        PreferenceConfiguration.UPSCALING_PREF_STRING, "off")) &&
                !preferences.getBoolean("checkbox_drop_late_frames", false) &&
                preferences.getBoolean("checkbox_reduce_refresh_rate", false) == (this == BATTERY_SAVER) &&
                preferences.getBoolean("checkbox_codec_performance", true) == (this != BATTERY_SAVER) &&
                !preferences.getBoolean("checkbox_phone_performance_hints", false);
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
                .putString(PreferenceConfiguration.UPSCALING_PREF_STRING, this == BEST_QUALITY ? "fsr1" : "off")
                .putString(PreferenceConfiguration.FRAME_PACING_PREF_STRING, pacing)
                .putBoolean("checkbox_drop_late_frames", false)
                .putBoolean("checkbox_reduce_refresh_rate", this == BATTERY_SAVER)
                .putBoolean("checkbox_codec_performance", this != BATTERY_SAVER)
                .putBoolean("checkbox_phone_performance_hints", false)
                .apply();
    }
}
