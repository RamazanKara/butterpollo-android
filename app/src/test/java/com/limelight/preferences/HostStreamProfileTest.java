package com.limelight.preferences;

import org.junit.Test;

import static org.junit.Assert.*;

public class HostStreamProfileTest {
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
        assertEquals("10", HostStreamProfile.formatBitrateMbps(10000));
        assertEquals("12.345", HostStreamProfile.formatBitrateMbps(12345));
        for (String value : new String[] {"0.4", "1000.001", "1.0005", "NaN", ""}) {
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
                "1280,720,6000,1000001,100,AUTO,false,false,false,false",
                "1280,720,6000,10000,201,AUTO,false,false,false,false"}) {
            assertThrows(IllegalArgumentException.class, () -> HostStreamProfile.deserialize(value));
        }
    }
}
