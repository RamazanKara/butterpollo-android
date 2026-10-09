package com.limelight.utils;

import org.junit.Test;
import static org.junit.Assert.*;

public class RedactedLogTest {
    @Test
    public void addressesPinsAndArbitraryHostRepliesNeverEnterTheExport() {
        RedactedLog log = new RedactedLog();
        for (String secret : new String[] {"192.168.1.12", "fe80::1234%wlan0", "::ffff:192.168.1.2",
                "AA:BB:CC:DD:EE:FF", "desktop.local", "my-private-host", "https://user:secret@host/pair?pin=0123",
                "PIN 1234", "passphrase hunter2", "Stream connected\nPIN=0000", "Pairing succeeded 0123"}) {
            assertNull(RedactedLog.redact(secret));
            log.add("INFO", secret);
            log.add("SEVERE", secret);
            assertFalse(log.snapshot().contains(secret));
        }
        assertFalse(log.snapshot().contains("192.168"));
        assertFalse(log.snapshot().contains("0123"));
        assertTrue(log.snapshot().contains("ERROR Diagnostic details omitted"));
    }

    @Test
    public void fixedEventsRemainUsefulAndOldEntriesAreEvicted() {
        RedactedLog log = new RedactedLog();
        log.add("INFO", "Stream starting");
        for (int i = 0; i < 200; i++) {
            log.add("INFO", "Stream connected");
        }
        assertEquals(200, log.snapshot().split("\n").length);
        assertFalse(log.snapshot().contains("Stream starting"));
        log.add("PIN 1234", "Stream disconnected");
        assertTrue(log.snapshot().endsWith("INFO Stream disconnected"));
        assertFalse(log.snapshot().contains("1234"));
    }
}
