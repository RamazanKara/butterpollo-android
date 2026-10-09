package com.limelight.preferences;

import android.content.Context;
import com.limelight.binding.video.UpscalingPolicy;

import java.math.BigDecimal;

public final class HostStreamProfile {
    private static final String PREFERENCES = "HostStreamProfiles";
    // Rubylight accepts up to 2 Gbps at stream setup; PyroWave's clean 4K60 needs about 1.6 Gbps.
    static final int MAX_BITRATE_KBPS = 2000000;

    public final int width, height, refreshRateX100, bitrate, renderScale;
    public final PreferenceConfiguration.FormatOption codec;
    public final boolean virtualDisplay, hdr, fullRange, yuv444;
    // Older profiles inherit VRR from the global settings.
    public final Boolean vrr;
    public final UpscalingPolicy.Mode upscalingMode;
    public final Integer upscalingSharpness;

    public HostStreamProfile(int width, int height, int refreshRateX100, int bitrate, int renderScale,
                             PreferenceConfiguration.FormatOption codec, boolean virtualDisplay,
                             boolean hdr, boolean fullRange, boolean yuv444) {
        this(width, height, refreshRateX100, bitrate, renderScale, codec, virtualDisplay, hdr, fullRange, yuv444, null);
    }

    public HostStreamProfile(int width, int height, int refreshRateX100, int bitrate, int renderScale,
                             PreferenceConfiguration.FormatOption codec, boolean virtualDisplay,
                             boolean hdr, boolean fullRange, boolean yuv444, Boolean vrr) {
        this(width, height, refreshRateX100, bitrate, renderScale, codec, virtualDisplay, hdr, fullRange, yuv444,
                vrr, null, null);
    }

    public HostStreamProfile(int width, int height, int refreshRateX100, int bitrate, int renderScale,
                             PreferenceConfiguration.FormatOption codec, boolean virtualDisplay,
                             boolean hdr, boolean fullRange, boolean yuv444, Boolean vrr,
                             UpscalingPolicy.Mode upscalingMode, Integer upscalingSharpness) {
        requireRange(width, 64, 16384);
        requireRange(height, 64, 16384);
        requireRange(refreshRateX100, 100, 100000);
        requireRange(bitrate, 500, MAX_BITRATE_KBPS);
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
        this.vrr = vrr;
        if ((upscalingMode == null) != (upscalingSharpness == null)) {
            throw new IllegalArgumentException("Incomplete upscaling profile");
        }
        if (upscalingSharpness != null) requireRange(upscalingSharpness, 0, 100);
        this.upscalingMode = upscalingMode;
        this.upscalingSharpness = upscalingSharpness;
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

    /** Parses Mbps with up to three decimals into kbps. */
    static int parseBitrateMbps(String text) {
        try {
            return requireRange(new BigDecimal(text.trim().replace(',', '.'))
                    .movePointRight(3).intValueExact(), 500, MAX_BITRATE_KBPS);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Bitrate needs at most three decimal places", e);
        }
    }

    static String formatBitrateMbps(int kbps) {
        return BigDecimal.valueOf(kbps, 3).stripTrailingZeros().toPlainString();
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
        if (vrr != null) {
            config.vrr = vrr;
        }
        if (upscalingMode != null) {
            config.upscalingMode = upscalingMode;
            config.upscalingSharpness = upscalingSharpness;
        }
        if (hdr || codec == PreferenceConfiguration.FormatOption.FORCE_PYROWAVE) {
            config.useTextureView = false;
        }
    }

    String serialize() {
        return width + "," + height + "," + refreshRateX100 + "," + bitrate + "," + renderScale +
                "," + codec.name() + "," + virtualDisplay + "," + hdr + "," + fullRange + "," + yuv444 +
                (upscalingMode == null ? (vrr == null ? "" : "," + vrr) :
                        "," + (vrr == null ? "" : vrr) + "," + upscalingMode.name() + "," + upscalingSharpness);
    }

    static HostStreamProfile deserialize(String value) {
        String[] fields = value.split(",", -1);
        if (fields.length != 10 && fields.length != 11 && fields.length != 13) {
            throw new IllegalArgumentException("Invalid host profile");
        }
        return new HostStreamProfile(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
                PreferenceConfiguration.FormatOption.valueOf(fields[5]),
                Boolean.parseBoolean(fields[6]), Boolean.parseBoolean(fields[7]),
                Boolean.parseBoolean(fields[8]), Boolean.parseBoolean(fields[9]),
                fields.length >= 11 && !fields[10].isEmpty() ? Boolean.parseBoolean(fields[10]) : null,
                fields.length == 13 ? UpscalingPolicy.Mode.valueOf(fields[11]) : null,
                fields.length == 13 ? Integer.parseInt(fields[12]) : null);
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
