package com.limelight.preferences;

import android.content.Context;

import java.math.BigDecimal;

public final class HostStreamProfile {
    private static final String PREFERENCES = "HostStreamProfiles";

    public final int width, height, refreshRateX100, bitrate, renderScale;
    public final PreferenceConfiguration.FormatOption codec;
    public final boolean virtualDisplay, hdr, fullRange, yuv444;

    public HostStreamProfile(int width, int height, int refreshRateX100, int bitrate, int renderScale,
                             PreferenceConfiguration.FormatOption codec, boolean virtualDisplay,
                             boolean hdr, boolean fullRange, boolean yuv444) {
        requireRange(width, 64, 16384);
        requireRange(height, 64, 16384);
        requireRange(refreshRateX100, 100, 100000);
        requireRange(bitrate, 500, 1000000);
        requireRange(renderScale, 50, 200);
        this.width = width;
        this.height = height;
        this.refreshRateX100 = refreshRateX100;
        this.bitrate = bitrate;
        this.renderScale = renderScale;
        this.codec = codec;
        this.virtualDisplay = virtualDisplay;
        this.hdr = hdr;
        this.fullRange = fullRange;
        this.yuv444 = yuv444;
    }

    static int requireRange(int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Value outside supported range");
        }
        return value;
    }

    static int parseRefreshRate(String text) {
        try {
            return requireRange(new BigDecimal(text.trim().replace(',', '.'))
                    .movePointRight(2).intValueExact(), 100, 100000);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Refresh rate needs at most two decimal places", e);
        }
    }

    static String formatRefreshRate(int refreshRateX100) {
        return BigDecimal.valueOf(refreshRateX100, 2).stripTrailingZeros().toPlainString();
    }

    void applyTo(PreferenceConfiguration config) {
        config.width = width;
        config.height = height;
        config.launchRefreshRateX100 = refreshRateX100;
        config.fps = Math.round(refreshRateX100 / 100f);
        config.bitrate = bitrate;
        config.virtualDisplayScale = renderScale;
        config.videoFormat = codec;
        config.virtualDisplay = virtualDisplay;
        config.enableHdr = hdr;
        config.fullRange = fullRange;
        config.enableYuv444 = yuv444;
        if (hdr || codec == PreferenceConfiguration.FormatOption.FORCE_PYROWAVE) {
            config.useTextureView = false;
        }
    }

    String serialize() {
        return width + "," + height + "," + refreshRateX100 + "," + bitrate + "," + renderScale +
                "," + codec.name() + "," + virtualDisplay + "," + hdr + "," + fullRange + "," + yuv444;
    }

    static HostStreamProfile deserialize(String value) {
        String[] fields = value.split(",", -1);
        if (fields.length != 10) {
            throw new IllegalArgumentException("Invalid host profile");
        }
        return new HostStreamProfile(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
                PreferenceConfiguration.FormatOption.valueOf(fields[5]),
                Boolean.parseBoolean(fields[6]), Boolean.parseBoolean(fields[7]),
                Boolean.parseBoolean(fields[8]), Boolean.parseBoolean(fields[9]));
    }

    static HostStreamProfile load(Context context, String hostUuid) {
        if (hostUuid == null) {
            return null;
        }
        String value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(hostUuid, null);
        if (value == null) {
            return null;
        }
        try {
            return deserialize(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    void save(Context context, String hostUuid) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(hostUuid, serialize()).apply();
    }

    static void reset(Context context, String hostUuid) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().remove(hostUuid).apply();
    }
}
