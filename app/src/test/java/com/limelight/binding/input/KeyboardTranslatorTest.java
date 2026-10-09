package com.limelight.binding.input;

import android.view.KeyEvent;
import org.junit.Test;
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
}
