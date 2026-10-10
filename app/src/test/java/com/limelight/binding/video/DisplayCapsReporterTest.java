package com.limelight.binding.video;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DisplayCapsReporterTest {
    private final List<float[]> sent = new ArrayList<>();
    private boolean delivered = true;
    private final DisplayCapsReporter reporter = new DisplayCapsReporter((hdr, max, average, min) -> {
        if (delivered) sent.add(new float[] {hdr ? 1 : 0, max, average, min});
        return delivered;
    });

    @Test
    public void sendsOnceUntilAValueChanges() {
        assertNotNull(reporter.update(true, 1000, 500, 0.0005f));
        assertNull(reporter.update(true, 1000, 500, 0.0005f));
        assertNull(reporter.update(true, 1000.001f, 500, 0.0005f));
        assertEquals(1, sent.size());

        // A foldable switching to its cover screen.
        assertNotNull(reporter.update(true, 800, 400, 0.0005f));
        assertNotNull(reporter.update(false, 800, 400, 0.0005f));
        assertNull(reporter.update(false, 800, 400, 0.0005f));
        assertEquals(3, sent.size());
        assertEquals(800, sent.get(2)[1], 0);
    }

    @Test
    public void blackLevelChangesAtWirePrecisionCount() {
        assertNotNull(reporter.update(true, 1000, 500, 0.0005f));
        assertNotNull(reporter.update(true, 1000, 500, 0.0006f));
        assertEquals(2, sent.size());
    }

    @Test
    public void failedSendIsRetriedOnTheNextUpdate() {
        delivered = false;
        assertNull(reporter.update(true, 1000, 500, 0.0005f));
        delivered = true;
        assertNotNull(reporter.update(true, 1000, 500, 0.0005f));
        assertEquals(1, sent.size());
    }

    @Test
    public void unknownAndImplausibleValuesBecomeZero() {
        DisplayCapsReporter.Caps caps = DisplayCapsReporter.Caps.of(false, -1, Float.NaN, Float.POSITIVE_INFINITY);
        assertEquals(0, caps.maxNits, 0);
        assertEquals(0, caps.maxAverageNits, 0);
        assertEquals(0, caps.minNits, 0);

        caps = DisplayCapsReporter.Caps.of(true, 50000, 20000, 1.5f);
        assertEquals(0, caps.maxNits, 0);
        assertEquals(0, caps.maxAverageNits, 0);
        assertEquals(0, caps.minNits, 0);

        caps = DisplayCapsReporter.Caps.of(true, 0.5f, 0.3f, 0.0005f);
        assertEquals(0, caps.maxNits, 0);
        assertEquals(0.3f, caps.maxAverageNits, 0);
        assertEquals(0.0005f, caps.minNits, 0);
    }

    @Test
    public void anSdrLevelReportedAsTheHdrPeakIsUnknown() {
        // What a Galaxy Z Fold 7 reports for a panel of about 2,600 nits.
        DisplayCapsReporter.Caps caps = DisplayCapsReporter.Caps.of(true, 400, 400, 0.0005f);
        assertEquals(0, caps.maxNits, 0);
        assertEquals(0, caps.maxAverageNits, 0);
        assertEquals(0.0005f, caps.minNits, 0);
        caps = DisplayCapsReporter.Caps.of(true, 500, 400, 0.0005f);
        assertEquals(500, caps.maxNits, 0);
        assertEquals(400, caps.maxAverageNits, 0);
    }

    @Test
    public void averageIsNeverAboveThePeak() {
        DisplayCapsReporter.Caps caps = DisplayCapsReporter.Caps.of(true, 600, 900, 0);
        assertEquals(600, caps.maxNits, 0);
        assertEquals(600, caps.maxAverageNits, 0);
        assertNotNull(reporter.update(true, 600, 900, 0));
        assertEquals(600, sent.get(0)[2], 0);
    }

    @Test
    public void logLineNamesTheValues() {
        String line = DisplayCapsReporter.Caps.of(true, 2600, 1200, 0.0005f).toString();
        assertEquals("HDR on, peak 2600 nits, frame average 1200 nits, black 0.0005 nits", line);
        assertTrue(DisplayCapsReporter.Caps.of(false, 0, 0, 0).toString().contains("peak unknown"));
    }
}
