package com.limelight.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Set;

public final class RedactedLog {
    private static final int MAX_ENTRIES = 200;
    static final int MAX_TRACE_BYTES = 64 * 1024;
    static final int MAX_TRACE_CHARS = 4096;
    private static final Set<String> NATIVE_LIBRARIES = Set.of("libc.so", "libart.so", "libandroid_runtime.so",
            "libmoonlight-core.so", "libpyrowave-renderer.so", "libpyrowave-shared.so", "libbinder.so", "libutils.so", "libhwui.so",
            "liblog.so", "libc++.so", "libc++_shared.so", "libbase.so", "libunwindstack.so", "base.apk");
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

    static String crashThread(String name) {
        // Networking libraries sometimes put the remote URL in the thread name.
        return name != null && name.length() <= 80 &&
                (name.equals("main") || name.equals("Crash history") || name.equals("FinalizerDaemon") ||
                        name.equals("FinalizerWatchdogDaemon") || name.equals("ReferenceQueueDaemon") ||
                        name.matches("(?:Thread-\\d+|pool-\\d+-thread-\\d+|AsyncTask #\\d+|GLThread \\d+)"))
                ? name : "[name omitted]";
    }

    static String nativeTrace(InputStream input) throws IOException {
        byte[] bytes = new byte[MAX_TRACE_BYTES];
        int length = 0;
        int count;
        while (length < bytes.length && (count = input.read(bytes, length, bytes.length - length)) != -1) {
            length += count;
        }
        StringBuilder text = new StringBuilder("Tombstone prefix (redacted; up to 64 KiB input):\n");
        try {
            appendTombstone(ByteBuffer.wrap(bytes, 0, length), 0, text);
        } catch (IOException e) {
            text.append("[trace incomplete]\n");
        }
        text.append("[messages, names, paths, registers, memory and logs omitted]\n");
        return text.substring(0, Math.min(text.length(), MAX_TRACE_CHARS));
    }

    private static void appendTombstone(ByteBuffer data, int section, StringBuilder text) throws IOException {
        // Only structural fields from debuggerd/proto/tombstone.proto cross the export boundary.
        // Sections: Tombstone, Signal, thread map entry, Thread, BacktraceFrame.
        long relativePc = 0;
        String library = "[library omitted]";
        while (data.hasRemaining() && text.length() < MAX_TRACE_CHARS) {
            long tag = readVarint(data);
            int field = (int) (tag >>> 3);
            int wire = (int) (tag & 7);
            if (field == 0) {
                throw new IOException("Invalid tombstone field");
            }
            if (wire == 0) {
                long value = readVarint(data);
                if (section == 0 && field == 6) {
                    text.append("Crashing thread ID: ").append(value).append('\n');
                } else if (section == 1 && (field == 1 || field == 3)) {
                    text.append(field == 1 ? "Signal: " : "Signal code: ").append((int) value).append('\n');
                } else if (section == 3 && field == 1) {
                    text.append("Thread ID: ").append(value).append('\n');
                } else if (section == 4 && field == 1) {
                    relativePc = value;
                }
            } else if (wire == 2) {
                long size = readVarint(data);
                if (size < 0 || size > Integer.MAX_VALUE) {
                    throw new IOException("Invalid tombstone length");
                }
                ByteBuffer child = data.slice();
                child.limit((int) Math.min(size, data.remaining()));
                data.position(data.position() + child.remaining());
                if (section == 0 && field == 10) {
                    appendTombstone(child, 1, text);
                } else if (section == 0 && field == 16) {
                    appendTombstone(child, 2, text);
                } else if (section == 2 && field == 2) {
                    appendTombstone(child, 3, text);
                } else if (section == 3 && field == 4) {
                    appendTombstone(child, 4, text);
                } else if (section == 4 && field == 6 && size == child.remaining() && size <= 512) {
                    String path = StandardCharsets.UTF_8.decode(child).toString();
                    String basename = path.substring(path.lastIndexOf('/') + 1);
                    if (NATIVE_LIBRARIES.contains(basename)) {
                        library = basename;
                    }
                }
            } else if ((wire == 1 && data.remaining() >= 8) || (wire == 5 && data.remaining() >= 4)) {
                data.position(data.position() + (wire == 1 ? 8 : 4));
            } else {
                throw new IOException("Invalid tombstone wire type");
            }
        }
        if (section == 4) {
            text.append("  pc ").append(Long.toHexString(relativePc)).append(' ').append(library).append('\n');
        }
    }

    private static long readVarint(ByteBuffer data) throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64 && data.hasRemaining(); shift += 7) {
            int value = data.get() & 0xff;
            result |= (long) (value & 0x7f) << shift;
            if ((value & 0x80) == 0) {
                return result;
            }
        }
        throw new IOException("Incomplete tombstone varint");
    }
}
