package com.limelight.utils;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Game entry files for ES-DE, Daijisho, Pegasus and other Android frontends.
 *
 * The format matches Artemis' ".art" files, so entries exported by Apollo tools also launch here:
 * "#" comment lines, then one "[key] value" pair per line. Frontends pass the file to
 * {@link com.limelight.ShortcutTrampoline} as an ACTION_VIEW content URI.
 */
public final class FrontendEntry {
    public static final String EXTENSION = ".art";
    public static final String KEY_HOST_UUID = "host_uuid";
    public static final String KEY_HOST_NAME = "host_name";
    public static final String KEY_APP_UUID = "app_uuid";
    public static final String KEY_APP_NAME = "app_name";
    public static final String KEY_APP_ID = "app_id";

    /** Entries are a few hundred bytes; anything larger is not one of ours. */
    public static final int MAX_CHARS = 16 * 1024;

    public static final String ES_SYSTEM_NAME = "butterpollo";
    public static final String ES_EMULATOR_NAME = "BUTTERPOLLO";

    private FrontendEntry() {}

    public static String serialize(String hostUuid, String hostName, String appUuid, String appName, int appId) {
        StringBuilder sb = new StringBuilder("# Butterpollo game entry\n");
        appendLine(sb, KEY_HOST_UUID, hostUuid);
        appendLine(sb, KEY_HOST_NAME, hostName);
        appendLine(sb, KEY_APP_UUID, appUuid);
        appendLine(sb, KEY_APP_NAME, appName);
        if (appId > 0) {
            appendLine(sb, KEY_APP_ID, Integer.toString(appId));
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String key, String value) {
        if (value == null) {
            return;
        }
        // Values are single-line by construction; strip line breaks a host name might carry
        String clean = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (!clean.isEmpty()) {
            sb.append('[').append(key).append("] ").append(clean).append('\n');
        }
    }

    /**
     * Parses an entry. Unknown keys are kept, malformed lines are rejected so a random file
     * handed to us by "Open with" never starts a stream.
     */
    public static Map<String, String> parse(Reader source) throws IOException {
        char[] contents = new char[MAX_CHARS + 1];
        int length = 0;
        int count;
        while ((count = source.read(contents, length, contents.length - length)) != -1) {
            length += count;
            if (length > MAX_CHARS) {
                throw new IOException("Entry file is too large");
            }
        }
        BufferedReader reader = new BufferedReader(new CharArrayReader(contents, 0, length));
        Map<String, String> values = new HashMap<>();
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Desktop editors may prefix the first line with a UTF-8 byte order mark.
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1).trim();
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int close = line.indexOf(']');
            if (!line.startsWith("[") || close < 2) {
                throw new IOException("Not a game entry file");
            }
            String key = line.substring(1, close).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(close + 1).trim();
            if (!value.isEmpty()) {
                values.put(key, value);
            }
        }
        if (!values.containsKey(KEY_HOST_UUID) && !values.containsKey(KEY_HOST_NAME)) {
            throw new IOException("Entry names no host");
        }
        return values;
    }

    /** A file name that works on FAT/exFAT SD cards and in frontends' file lists. */
    public static String fileBaseName(String appName) {
        String name = appName == null ? "" : appName;
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || "\\/:*?\"<>|".indexOf(c) >= 0) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        String base = sb.toString().replaceAll("\\s+", " ").trim();
        // Leading dots hide files; trailing dots and spaces are dropped by FAT
        base = base.replaceAll("^[.\\s]+", "").replaceAll("[.\\s]+$", "");
        if (base.length() > 120) {
            base = base.substring(0, 120).trim();
        }
        return base.isEmpty() ? "Game" : base;
    }

    public static String esSystemBlock(String hostLabel) {
        return "    <system>\n" +
                "        <name>" + ES_SYSTEM_NAME + "</name>\n" +
                "        <fullname>" + xmlEscape(hostLabel) + "</fullname>\n" +
                "        <path>%ROMPATH%/" + ES_SYSTEM_NAME + "</path>\n" +
                "        <extension>" + EXTENSION + " " + EXTENSION.toUpperCase(Locale.ROOT) + "</extension>\n" +
                "        <command label=\"Butterpollo\">%EMULATOR_" + ES_EMULATOR_NAME + "% %ACTIVITY_CLEAR_TASK% %ACTIVITY_CLEAR_TOP% " +
                "%ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%</command>\n" +
                "        <platform>pc</platform>\n" +
                "        <theme>windows</theme>\n" +
                "    </system>\n";
    }

    public static String esFindRuleBlock(String packageName) {
        return "    <emulator name=\"" + ES_EMULATOR_NAME + "\">\n" +
                "        <!-- Butterpollo game streaming client -->\n" +
                "        <rule type=\"androidpackage\">\n" +
                "            <entry>" + xmlEscape(packageName) + "/com.limelight.ShortcutTrampoline</entry>\n" +
                "        </rule>\n" +
                "    </emulator>\n";
    }

    /** Adds or replaces our system in an existing ES-DE custom_systems/es_systems.xml. */
    public static String mergeEsSystems(String existing, String hostLabel) {
        return mergeBlock(existing, "systemList", "<system>", "</system>",
                "<name>" + ES_SYSTEM_NAME + "</name>", esSystemBlock(hostLabel));
    }

    /** Adds or replaces our emulator in an existing ES-DE custom_systems/es_find_rules.xml. */
    public static String mergeEsFindRules(String existing, String packageName) {
        return mergeBlock(existing, "ruleList", "<emulator", "</emulator>",
                "name=\"" + ES_EMULATOR_NAME + "\"", esFindRuleBlock(packageName));
    }

    private static String mergeBlock(String existing, String root, String open, String close,
                                     String marker, String block) {
        String closeRoot = "</" + root + ">";
        if (existing == null || !existing.contains(closeRoot)) {
            return "<?xml version=\"1.0\"?>\n<" + root + ">\n" + block + closeRoot + "\n";
        }
        int markerAt = existing.indexOf(marker);
        if (markerAt >= 0) {
            int start = existing.lastIndexOf(open, markerAt);
            int end = existing.indexOf(close, markerAt);
            if (start >= 0 && end >= 0) {
                // Replace from the start of our block's line through the end of its closing line
                int lineStart = existing.lastIndexOf('\n', start) + 1;
                int lineEnd = existing.indexOf('\n', end);
                lineEnd = lineEnd < 0 ? existing.length() : lineEnd + 1;
                return existing.substring(0, lineStart) + block + existing.substring(lineEnd);
            }
        }
        int insertAt = existing.lastIndexOf(closeRoot);
        int lineStart = existing.lastIndexOf('\n', insertAt) + 1;
        if (existing.substring(lineStart, insertAt).trim().isEmpty()) {
            insertAt = lineStart;
        }
        String before = existing.substring(0, insertAt);
        if (!before.endsWith("\n")) {
            before += "\n";
        }
        return before + block + existing.substring(insertAt);
    }

    static String xmlEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
