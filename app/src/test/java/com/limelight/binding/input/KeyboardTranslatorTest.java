package com.limelight.binding.input;

import android.view.KeyEvent;
import com.limelight.nvstream.input.KeyboardPacket;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class KeyboardTranslatorTest {
    @Test
    public void numpadAndPrintScreenHaveWindowsVirtualKeys() {
        assertEquals((short) 0x800D, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_NUMPAD_ENTER));
        assertEquals((short) 0x806C, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_NUMPAD_COMMA));
        assertEquals((short) 0x802C, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_SYSRQ));
        assertEquals(KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_SCREENSHOT),
                KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_SYSRQ));
        assertEquals(0, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_UNKNOWN));
    }

    @Test
    public void leftAndRightModifiersRemainDistinctIncludingAltGr() {
        assertEquals((short) 0x80A4, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_ALT_LEFT));
        assertEquals((short) 0x80A5, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_ALT_RIGHT));
        assertEquals((short) 0x80A2, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_CTRL_LEFT));
        assertEquals((short) 0x80A3, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_CTRL_RIGHT));
    }

    @Test
    public void unicodeKeepsSupplementaryCharactersAndRejectsDeadKeys() {
        assertEquals("é", KeyboardTranslator.textForCodePoint(0xe9));
        assertEquals("\uD83D\uDE00", KeyboardTranslator.textForCodePoint(0x1f600));
        for (int invalid : new int[] {0, -1, 0x80000065, 0xd800, 0xdfff, 0x110000}) {
            assertNull(KeyboardTranslator.textForCodePoint(invalid));
        }
    }

    @Test
    public void committedAndMultipleTextKeepsUnicodeIntact() {
        List<String> text = new ArrayList<>();
        assertTrue(KeyboardTranslator.sendTextInput("hello", text::add,
                (key, direction) -> fail("Printable text became a raw key")));
        assertTrue(KeyboardTranslator.sendTextInput(new StringBuilder("é\uD83D\uDE00中文"), text::add,
                (key, direction) -> fail("Unicode text became a raw key")));
        assertEquals(Arrays.asList("hello", "é\uD83D\uDE00中文"), text);
    }

    @Test
    public void backspaceAndEnterSendBalancedKeysInTextOrder() {
        List<Object> events = new ArrayList<>();
        assertTrue(KeyboardTranslator.sendTextInput("hello\b\nworld", events::add,
                (key, direction) -> {
                    events.add(key);
                    events.add(direction);
                }));
        assertEquals(Arrays.asList("hello", (short) 0x8008, KeyboardPacket.KEY_DOWN,
                (short) 0x8008, KeyboardPacket.KEY_UP, (short) 0x800D, KeyboardPacket.KEY_DOWN,
                (short) 0x800D, KeyboardPacket.KEY_UP, "world"), events);
        assertEquals((short) 0x8008, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_DEL));
        assertEquals((short) 0x800D, KeyboardTranslator.translateKeyCode(KeyEvent.KEYCODE_ENTER));
    }

    @Test
    public void carriageReturnLineFeedSendsOneEnterPerLineBreak() {
        List<Byte> directions = new ArrayList<>();
        assertTrue(KeyboardTranslator.sendTextInput("\r\n\r\n", text -> fail("Sent control text"),
                (key, direction) -> {
                    assertEquals((short) 0x800D, key.shortValue());
                    directions.add(direction);
                }));
        assertEquals(Arrays.asList(KeyboardPacket.KEY_DOWN, KeyboardPacket.KEY_UP,
                KeyboardPacket.KEY_DOWN, KeyboardPacket.KEY_UP), directions);
    }

    @Test
    public void missingOrEmptyTextSendsNothing() {
        assertFalse(KeyboardTranslator.sendTextInput(null, text -> fail("Sent null text"),
                (key, direction) -> fail("Sent a key for null text")));
        assertTrue(KeyboardTranslator.sendTextInput("", text -> fail("Sent empty text"),
                (key, direction) -> fail("Sent a key for empty text")));
    }
}
