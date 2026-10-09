package com.limelight.utils;

import java.util.ArrayDeque;
import java.util.Set;

public final class RedactedLog {
    private static final int MAX_ENTRIES = 200;
    private static final Set<String> EVENTS = Set.of(
            "Stream starting", "Stream connected", "Stream stopping", "Stream failed",
            "Stream disconnected", "Stream activity destroyed", "Stream restored after process death",
            "Pairing started", "Pairing succeeded", "Pairing failed", "Pairing cancelled",
            "USB controller attached", "USB controller detached", "USB permission denied",
            "Bluetooth or Android controller detached", "Connection test started", "Connection test finished");
    private final ArrayDeque<String> entries = new ArrayDeque<>();

    public synchronized void add(String level, String message) {
        // Only fixed event text crosses the export boundary. Host replies can contain arbitrary secrets.
        String safeMessage = redact(message);
        if (safeMessage == null) {
            if (!"WARNING".equals(level) && !"SEVERE".equals(level)) {
                return;
            }
            safeMessage = "Diagnostic details omitted";
        }
        if (entries.size() == MAX_ENTRIES) {
            entries.removeFirst();
        }
        entries.addLast(("SEVERE".equals(level) ? "ERROR" : "WARNING".equals(level) ? "WARNING" : "INFO")
                + " " + safeMessage);
    }

    static String redact(String message) {
        return message != null && EVENTS.contains(message) ? message : null;
    }

    public synchronized String snapshot() {
        return String.join("\n", entries);
    }
}
