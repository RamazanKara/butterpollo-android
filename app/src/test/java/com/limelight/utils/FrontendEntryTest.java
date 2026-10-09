package com.limelight.utils;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FrontendEntryTest {
    private static Map<String, String> parse(String text) throws IOException {
        return FrontendEntry.parse(new StringReader(text));
    }

    @Test
    public void roundTripsAnEntry() throws IOException {
        String text = FrontendEntry.serialize("host-uuid", "Gaming PC", "app-uuid", "Elden Ring", 42);
        Map<String, String> values = parse(text);
        assertEquals("host-uuid", values.get(FrontendEntry.KEY_HOST_UUID));
        assertEquals("Gaming PC", values.get(FrontendEntry.KEY_HOST_NAME));
        assertEquals("app-uuid", values.get(FrontendEntry.KEY_APP_UUID));
        assertEquals("Elden Ring", values.get(FrontendEntry.KEY_APP_NAME));
        assertEquals("42", values.get(FrontendEntry.KEY_APP_ID));
    }

    @Test
    public void readsArtemisExports() throws IOException {
        // As written by ApolloLauncherExport's generic generator
        Map<String, String> values = parse("# Artemis app entry\n" +
                "[host_uuid] 1234\n[host_name] Desk\n[app_uuid] ABCD\n[app_name] Steam Big Picture\n");
        assertEquals("1234", values.get(FrontendEntry.KEY_HOST_UUID));
        assertEquals("Steam Big Picture", values.get(FrontendEntry.KEY_APP_NAME));
        assertFalse(values.containsKey(FrontendEntry.KEY_APP_ID));
    }

    @Test
    public void toleratesBomCrlfAndBlankValues() throws IOException {
        Map<String, String> values = parse("\uFEFF[host_name] Desk\r\n[app_uuid]\r\n\r\n[APP_NAME] Hades\r\n");
        assertEquals("Desk", values.get(FrontendEntry.KEY_HOST_NAME));
        assertEquals("Hades", values.get(FrontendEntry.KEY_APP_NAME));
        assertFalse(values.containsKey(FrontendEntry.KEY_APP_UUID));
    }

    @Test
    public void rejectsFilesThatAreNotEntries() {
        String[] bad = {
                "PK\u0003\u0004 binary",
                "[app_name] No host here\n",
                "[] empty key\n",
                "",
        };
        for (String text : bad) {
            try {
                parse(text);
                fail("accepted: " + text);
            } catch (IOException expected) {
                // expected
            }
        }
    }

    @Test
    public void rejectsOversizedFiles() {
        StringBuilder sb = new StringBuilder("[host_name] Desk\n");
        while (sb.length() <= FrontendEntry.MAX_CHARS) {
            sb.append("# padding padding padding padding padding padding\n");
        }
        try {
            parse(sb.toString());
            fail("accepted oversized entry");
        } catch (IOException expected) {
            // expected
        }
    }

    @Test
    public void rejectsAnOversizedLineBeforeReadingTheRestOfIt() {
        Reader source = new Reader() {
            private int read;

            @Override
            public int read(char[] buffer, int offset, int length) {
                assertTrue("Read beyond the entry size limit", read + length <= FrontendEntry.MAX_CHARS + 1);
                Arrays.fill(buffer, offset, offset + length, '#');
                read += length;
                return length;
            }

            @Override
            public void close() { }
        };
        assertThrows(IOException.class, () -> FrontendEntry.parse(source));
    }

    @Test
    public void acceptsABomBeforeAnExportedCommentOrBlankLine() throws IOException {
        assertEquals("Desk", parse("\uFEFF" + FrontendEntry.serialize(null, "Desk", null, "Game", 42))
                .get(FrontendEntry.KEY_HOST_NAME));
        assertEquals("Desk", parse("\uFEFF\r\n[host_name] Desk\r\n")
                .get(FrontendEntry.KEY_HOST_NAME));
    }

    @Test
    public void acceptsAnEntryAtTheCharacterLimitWithoutAFinalNewline() throws IOException {
        String entry = "[host_name] Desk";
        assertEquals("Desk", parse(entry + " ".repeat(FrontendEntry.MAX_CHARS - entry.length()))
                .get(FrontendEntry.KEY_HOST_NAME));
    }

    @Test
    public void serializeDropsLineBreaksAndMissingFields() throws IOException {
        String text = FrontendEntry.serialize("u", "Two\nLines", null, "Game", 0);
        assertFalse(text.contains("[app_uuid]"));
        assertFalse(text.contains("[app_id]"));
        assertEquals("Two Lines", parse(text).get(FrontendEntry.KEY_HOST_NAME));
    }

    @Test
    public void fileNamesAreSafeForSdCards() {
        assertEquals("Half-Life 2 Episode One", FrontendEntry.fileBaseName("Half-Life 2: Episode One"));
        assertEquals("a b", FrontendEntry.fileBaseName("a/\\b"));
        assertEquals("Game", FrontendEntry.fileBaseName("..."));
        assertEquals("Game", FrontendEntry.fileBaseName(null));
        assertEquals("Desktop", FrontendEntry.fileBaseName(".Desktop. "));
        assertTrue(FrontendEntry.fileBaseName(new String(new char[300]).replace('\0', 'x')).length() <= 120);
    }

    @Test
    public void createsEsDeFilesWhenMissing() {
        String systems = FrontendEntry.mergeEsSystems(null, "Butterpollo & Co");
        assertTrue(systems.startsWith("<?xml"));
        assertTrue(systems.contains("<name>butterpollo</name>"));
        assertTrue(systems.contains("<fullname>Butterpollo &amp; Co</fullname>"));
        assertTrue(systems.contains("%EMULATOR_BUTTERPOLLO%"));
        assertTrue(systems.contains("%DATA%=%ROMPROVIDER%"));
        assertTrue(systems.trim().endsWith("</systemList>"));

        String rules = FrontendEntry.mergeEsFindRules("", "com.butterpollo.client");
        assertTrue(rules.contains("<entry>com.butterpollo.client/com.limelight.ShortcutTrampoline</entry>"));
        assertTrue(rules.trim().endsWith("</ruleList>"));
    }

    @Test
    public void keepsOtherCustomSystemsAndReplacesOurs() {
        String other = "    <system>\n        <name>n64</name>\n        <path>/sdcard/n64</path>\n    </system>\n";
        String existing = "<?xml version=\"1.0\"?>\n<systemList>\n" + other + "</systemList>\n";

        String once = FrontendEntry.mergeEsSystems(existing, "Old name");
        assertTrue(once.contains(other));
        assertTrue(once.contains("Old name"));

        String twice = FrontendEntry.mergeEsSystems(once, "New name");
        assertTrue(twice.contains(other));
        assertFalse(twice.contains("Old name"));
        assertEquals(1, count(twice, "<name>butterpollo</name>"));
        assertEquals(1, count(twice, "</systemList>"));
    }

    @Test
    public void keepsOtherFindRulesAndReplacesOurs() {
        String existing = "<ruleList>\n    <emulator name=\"SPECCY\">\n        <rule type=\"androidpackage\">\n" +
                "            <entry>a/b</entry>\n        </rule>\n    </emulator>\n</ruleList>";
        String once = FrontendEntry.mergeEsFindRules(existing, "com.butterpollo.client");
        String twice = FrontendEntry.mergeEsFindRules(once, "com.butterpollo.client.root");
        assertTrue(twice.contains("<emulator name=\"SPECCY\">"));
        assertTrue(twice.contains("com.butterpollo.client.root/"));
        assertEquals(1, count(twice, "name=\"BUTTERPOLLO\""));
        assertEquals(2, count(twice, "</emulator>"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
