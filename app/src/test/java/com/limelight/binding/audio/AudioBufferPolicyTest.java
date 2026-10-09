package com.limelight.binding.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioBufferPolicyTest {
    @Test public void underrunsGrowOneBurstAndCleanPlaybackRecoversSlowly() {
        AudioBufferPolicy policy = new AudioBufferPolicy(192, 1920, 384, 0, 0);
        assertEquals(384, policy.sample(10, 999));
        assertEquals(576, policy.sample(10, 1000));
        assertEquals(768, policy.sample(11, 2000));
        assertEquals(768, policy.sample(11, 31000));
        assertEquals(576, policy.sample(11, 32000));
        assertEquals(384, policy.sample(11, 62000));
        assertEquals(384, policy.sample(11, 92000));
    }

    @Test public void growthIsBoundedByCapacityAndEightBursts() {
        for (int capacity : new int[] {576, 1920}) {
            AudioBufferPolicy policy = new AudioBufferPolicy(192, capacity, 384, 0, 0);
            int frames = 0;
            for (int i = 1; i < 100; i++) frames = policy.sample(i, i * 1000L);
            assertEquals(Math.min(capacity, 1536), frames);
        }
    }

    @Test public void counterResetsAndMissingMeasurementsCannotTriggerShrink() {
        AudioBufferPolicy policy = new AudioBufferPolicy(240, 2400, 480, 10, 0);
        assertEquals(720, policy.sample(11, 1000));
        assertEquals(720, policy.sample(-1, 31000));
        assertEquals(720, policy.sample(0, 32000));
        policy.applied(960);
        assertEquals(960, policy.sample(0, 33000));
    }

    @Test public void platformMinimumAndRejectedSizeRequestsRemainSafe() {
        AudioBufferPolicy policy = new AudioBufferPolicy(120, 2400, 1440, 0, 0);
        assertEquals(1440, policy.sample(0, 30000));
        policy.applied(-1);
        assertEquals(1440, policy.sample(1, 31000));
    }

    @Test public void oldApiSurroundEffectsAndResamplingUseAudioTrack() {
        assertTrue(AudioBufferPolicy.useAAudio(27, 2, false, 48000, 48000));
        assertFalse(AudioBufferPolicy.useAAudio(26, 2, false, 48000, 48000));
        assertFalse(AudioBufferPolicy.useAAudio(36, 6, false, 48000, 48000));
        assertFalse(AudioBufferPolicy.useAAudio(36, 8, false, 48000, 48000));
        assertFalse(AudioBufferPolicy.useAAudio(36, 2, true, 48000, 48000));
        assertFalse(AudioBufferPolicy.useAAudio(36, 2, false, 48000, 44100));
    }
}
