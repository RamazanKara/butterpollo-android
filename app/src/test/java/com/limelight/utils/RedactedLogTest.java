package com.limelight.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class RedactedLogTest {
    @Test
    public void addressesPinsAndKnownNamesNeverEnterTheExport() {
        RedactedLog.addSecret("desktop.local");
        RedactedLog.addSecret("my-private-host");
        RedactedLog log = new RedactedLog();
        String[] secrets = {"192.168.1.12", "fe80::1234%wlan0", "::ffff:192.168.1.2",
                "AA:BB:CC:DD:EE:FF", "desktop.local", "my-private-host", "https://user:secret@host/pair?pin=0123",
                "PIN 1234", "passphrase hunter2", "Stream connected\nPIN=0000", "Pairing succeeded 0123",
                "3fa85f64-5717-4562-b3fc-2c963f66afa6", "/storage/emulated/0/Download/report.txt"};
        for (String secret : secrets) {
            log.add("SEVERE", "Connection to " + secret + " failed");
        }
        String snapshot = log.snapshot();
        for (String part : new String[] {"192.168", "fe80", "ffff", "AA:BB", "desktop.local", "my-private-host",
                "user:secret", "0123", "1234", "hunter2", "0000", "3fa85f64", "/storage", "report.txt"}) {
            assertFalse(part, snapshot.contains(part));
        }
        assertTrue(snapshot.contains("ERROR Connection to [PC] failed"));
    }

    @Test
    public void ourOwnErrorsAndKeyLinesStayReadable() {
        RedactedLog log = new RedactedLog();
        log.add("SEVERE", "Connection terminated: -102 (ended right after starting)");
        log.add("WARNING", "java.lang.IllegalStateException at MediaCodecDecoderRenderer.queueNextInputBuffer");
        log.add("INFO", "Stream request: 2184x1968 at 120 fps (resolution native, PC profile)");
        log.add("INFO", "Selected HEVC decoder: c2.qti.hevc.decoder");
        log.add("INFO", "frame 12345 queued");
        String snapshot = log.snapshot();
        assertTrue(snapshot.contains("ERROR Connection terminated: -102 (ended right after starting)"));
        assertTrue(snapshot.contains("IllegalStateException at MediaCodecDecoderRenderer.queueNextInputBuffer"));
        assertTrue(snapshot.contains("INFO Stream request: 2184x1968 at 120 fps (resolution native, PC profile)"));
        assertTrue(snapshot.contains("c2.qti.hevc.decoder"));
        assertFalse(snapshot.contains("frame 12345"));
    }

    @Test
    public void fixedEventsRemainUsefulAndOldEntriesAreEvicted() {
        RedactedLog log = new RedactedLog();
        log.add("INFO", "Stream starting");
        for (int i = 0; i < 400; i++) {
            log.add("INFO", "Stream connected");
        }
        assertEquals(400, log.snapshot().split("\n").length);
        assertFalse(log.snapshot().contains("Stream starting"));
        log.add("PIN 1234", "Stream disconnected");
        assertTrue(log.snapshot().endsWith("INFO Stream disconnected"));
        assertFalse(log.snapshot().contains("1234"));
    }
}
