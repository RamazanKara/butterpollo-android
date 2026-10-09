package com.limelight.preferences;

import org.junit.Test;
import com.limelight.binding.video.UpscalingPolicy;

import static org.junit.Assert.*;

public class HostStreamProfileTest {
    @Test
    public void oldProfilesInheritVrrAndNewProfilesKeepTheirOverride() {
        String legacy = "1920,1080,5994,45500,100,AUTO,false,false,false,false";
        HostStreamProfile profile = HostStreamProfile.deserialize(legacy);
        assertNull(profile.vrr);
        assertEquals(legacy, profile.serialize());
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.vrr = true;
        profile.applyTo(config);
        assertTrue(config.vrr);
        HostStreamProfile.deserialize(legacy + ",false").applyTo(config);
        assertFalse(config.vrr);
        HostStreamProfile enabled = HostStreamProfile.deserialize(legacy + ",true");
        enabled.applyTo(config);
        assertTrue(config.vrr);
        assertEquals(legacy + ",true", enabled.serialize());
    }

    @Test
    public void oldProfilesInheritUpscalingAndNewProfilesOverrideBothControls() {
        String legacy = "1920,1080,5994,45500,100,AUTO,false,false,false,false,true";
        PreferenceConfiguration config = new PreferenceConfiguration();
        assertEquals(UpscalingPolicy.Mode.OFF, config.upscalingMode);
        assertEquals(50, config.upscalingSharpness);
        config.upscalingMode = UpscalingPolicy.Mode.FSR1;
        config.upscalingSharpness = 70;
        HostStreamProfile.deserialize(legacy).applyTo(config);
        assertEquals(UpscalingPolicy.Mode.FSR1, config.upscalingMode);
        assertEquals(70, config.upscalingSharpness);
        for (UpscalingPolicy.Mode mode : UpscalingPolicy.Mode.values()) {
            for (int sharpness : new int[] {0, 50, 100}) {
                String value = legacy + "," + mode.name() + "," + sharpness;
                HostStreamProfile restored = HostStreamProfile.deserialize(value);
                assertEquals(value, restored.serialize());
                restored.applyTo(config);
                assertEquals(mode, config.upscalingMode);
                assertEquals(sharpness, config.upscalingSharpness);
                assertTrue(config.vrr);
            }
        }
    }

    @Test
    public void invalidUpscalingProfilesAreRejectedAndNullableLegacyVrrIsPreserved() {
        String prefix = "1280,720,6000,10000,100,AUTO,false,false,false,false,";
        HostStreamProfile inheritedVrr = HostStreamProfile.deserialize(prefix + ",FSR1,50");
        assertNull(inheritedVrr.vrr);
        assertEquals(prefix + ",FSR1,50", inheritedVrr.serialize());
        for (String suffix : new String[] {"true,FSR1,-1", "true,FSR1,101", "true,FSR1", "true,unknown,50"}) {
            assertThrows(IllegalArgumentException.class, () -> HostStreamProfile.deserialize(prefix + suffix));
        }
    }

    @Test
    public void fractionalRefreshIsExactAndAcceptsLocalizedDecimalSeparator() {
        assertEquals(5994, HostStreamProfile.parseRefreshRate("59.94"));
        assertEquals(11988, HostStreamProfile.parseRefreshRate("119,88"));
        assertEquals(24000, HostStreamProfile.parseRefreshRate(" 240 "));
        assertEquals("59.94", HostStreamProfile.formatRefreshRate(5994));
        assertEquals("120", HostStreamProfile.formatRefreshRate(12000));
    }

    @Test
    public void bitrateIsEnteredInMbpsAndStoredInKbps() {
        assertEquals(280000, HostStreamProfile.parseBitrateMbps("280"));
        assertEquals(10500, HostStreamProfile.parseBitrateMbps("10,5"));
        assertEquals(500, HostStreamProfile.parseBitrateMbps(" 0.5 "));
        assertEquals(1600000, HostStreamProfile.parseBitrateMbps("1600"));
        assertEquals("10", HostStreamProfile.formatBitrateMbps(10000));
        assertEquals("12.345", HostStreamProfile.formatBitrateMbps(12345));
        for (String value : new String[] {"0.4", "2000.001", "1.0005", "NaN", ""}) {
            assertThrows(value, IllegalArgumentException.class, () -> HostStreamProfile.parseBitrateMbps(value));
        }
    }

    @Test
    public void invalidRefreshCannotBeSilentlyRoundedOrOverflow() {
        for (String value : new String[] {"59.999", "0", "1000.01", "NaN", "Infinity", "2147483648", ""}) {
            assertThrows(value, IllegalArgumentException.class, () -> HostStreamProfile.parseRefreshRate(value));
        }
    }

    @Test
    public void profileSurvivesStorageAndAppliesAllStreamOverrides() {
        HostStreamProfile profile = new HostStreamProfile(1600, 2560, 11988, 280000, 150,
                PreferenceConfiguration.FormatOption.FORCE_PYROWAVE, true, true, true, true);
        HostStreamProfile restored = HostStreamProfile.deserialize(profile.serialize());
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.useTextureView = true;
        config.multiController = true;
        config.touchscreenTrackpad = true;
        restored.applyTo(config);

        assertEquals(1600, config.width);
        assertEquals(2560, config.height);
        assertEquals(11988, config.launchRefreshRateX100);
        assertEquals(120, config.fps);
        assertEquals(280000, config.bitrate);
        assertEquals(150, config.virtualDisplayScale);
        assertEquals(PreferenceConfiguration.FormatOption.FORCE_PYROWAVE, config.videoFormat);
        assertTrue(config.virtualDisplay);
        assertTrue(config.enableHdr);
        assertTrue(config.fullRange);
        assertTrue(config.enableYuv444);
        assertFalse(config.useTextureView);
        assertTrue(config.multiController);
        assertTrue(config.touchscreenTrackpad);
        assertEquals(profile.serialize(), restored.serialize());
    }

    @Test
    public void ordinarySdrProfileKeepsTextureViewAndCanDisableHostPreferences() {
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.useTextureView = true;
        config.virtualDisplay = config.enableHdr = config.fullRange = config.enableYuv444 = true;
        new HostStreamProfile(1280, 720, 6000, 10000, 100,
                PreferenceConfiguration.FormatOption.AUTO, false, false, false, false).applyTo(config);
        assertTrue(config.useTextureView);
        assertFalse(config.virtualDisplay);
        assertFalse(config.enableHdr);
        assertFalse(config.fullRange);
        assertFalse(config.enableYuv444);
    }

    @Test
    public void hdrAndPyrowaveIndependentlyRequireSurfaceView() {
        PreferenceConfiguration config = new PreferenceConfiguration();
        config.useTextureView = true;
        new HostStreamProfile(1280, 720, 6000, 10000, 100,
                PreferenceConfiguration.FormatOption.FORCE_HEVC, false, true, false, false).applyTo(config);
        assertFalse(config.useTextureView);
        config.useTextureView = true;
        new HostStreamProfile(1280, 720, 6000, 280000, 100,
                PreferenceConfiguration.FormatOption.FORCE_PYROWAVE, false, false, false, false).applyTo(config);
        assertFalse(config.useTextureView);
    }

    @Test
    public void malformedAndOutOfRangeStoredValuesAreRejected() {
        for (String value : new String[] {
                "1280,720,6000", "1280,720,6000,10000,100,UNKNOWN,false,false,false,false",
                "0,720,6000,10000,100,AUTO,false,false,false,false",
                "1280,16385,6000,10000,100,AUTO,false,false,false,false",
                "1280,720,6000,499,100,AUTO,false,false,false,false",
                "1280,720,6000,2000001,100,AUTO,false,false,false,false",
                "1280,720,6000,10000,201,AUTO,false,false,false,false"}) {
            assertThrows(IllegalArgumentException.class, () -> HostStreamProfile.deserialize(value));
        }
    }
}
