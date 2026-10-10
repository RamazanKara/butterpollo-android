package com.limelight.preferences;

import android.content.SharedPreferences;

import com.limelight.binding.video.DisplayFrameRatePolicy;

public enum StreamPreset {
    BALANCED("auto", 15000, false, "balanced", "off"),
    LOW_LATENCY(null, 15000, true, "latency", "sgsr1"),
    BEST_QUALITY("auto", 40000, false, "balanced", "fsr1"),
    BATTERY_SAVER("auto", 8000, false, "cap-fps", "bilinear");

    public final String codec, pacing;
    public final int bitrate;
    public final boolean vrr;
    private final String upscaling;

    StreamPreset(String codec, int bitrate, boolean vrr, String pacing, String upscaling) {
        this.codec = codec;
        this.bitrate = bitrate;
        this.vrr = vrr;
        this.pacing = pacing;
        this.upscaling = upscaling;
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
                upscaling.equals(preferences.getString(
                        PreferenceConfiguration.UPSCALING_PREF_STRING, "off")) &&
                !preferences.getBoolean("checkbox_drop_late_frames", false) &&
                preferences.getBoolean("checkbox_reduce_refresh_rate", false) == (this == BATTERY_SAVER) &&
                preferences.getBoolean("checkbox_codec_performance", true) == (this != BATTERY_SAVER) &&
                !preferences.getBoolean("checkbox_phone_performance_hints", false);
    }

    // True while every value a preset sets is still at its default. PcView writes the XML defaults
    // on first launch, so presence of a key says nothing; compare values instead.
    static boolean isUntouched(SharedPreferences preferences) {
        String fps = preferences.getString(PreferenceConfiguration.FPS_PREF_STRING, PreferenceConfiguration.DEFAULT_FPS);
        int defaultBitrate = PreferenceConfiguration.getDefaultBitrate(preferences.getString(
                PreferenceConfiguration.RESOLUTION_PREF_STRING, PreferenceConfiguration.DEFAULT_RESOLUTION), fps);
        return "auto".equals(preferences.getString(PreferenceConfiguration.VIDEO_FORMAT_PREF_STRING, "auto")) &&
                preferences.getInt(PreferenceConfiguration.BITRATE_PREF_STRING, defaultBitrate) == defaultBitrate &&
                PreferenceConfiguration.DEFAULT_FPS.equals(fps) &&
                !preferences.getBoolean("checkbox_vrr", false) &&
                PreferenceConfiguration.DEFAULT_FRAME_PACING.equals(preferences.getString(
                        PreferenceConfiguration.FRAME_PACING_PREF_STRING, PreferenceConfiguration.DEFAULT_FRAME_PACING)) &&
                "off".equals(preferences.getString(PreferenceConfiguration.UPSCALING_PREF_STRING, "off")) &&
                !preferences.getBoolean("checkbox_drop_late_frames", false) &&
                !preferences.getBoolean("checkbox_reduce_refresh_rate", false) &&
                preferences.getBoolean("checkbox_codec_performance", true) &&
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
                .putString(PreferenceConfiguration.UPSCALING_PREF_STRING, upscaling)
                .putString(PreferenceConfiguration.FRAME_PACING_PREF_STRING, pacing)
                .putBoolean("checkbox_drop_late_frames", false)
                .putBoolean("checkbox_reduce_refresh_rate", this == BATTERY_SAVER)
                .putBoolean("checkbox_codec_performance", this != BATTERY_SAVER)
                .putBoolean("checkbox_phone_performance_hints", false)
                .apply();
    }
}
