package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

/** The keyboard decisions that don't need a device: Back as Meta, and when keys are remapped to QWERTY. */
public class KeyboardTranslatorTest {
    private static final short VK_LWIN = 0x5b;

    @Test
    public void backIsTheWindowsKeyWhenAsked() {
        assertEquals(VK_LWIN, KeyboardTranslator.backAsMetaKey(true, KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void backIsNotSentWhenNotAsked() {
        assertEquals(0, KeyboardTranslator.backAsMetaKey(false, KeyEvent.KEYCODE_BACK));
    }

    @Test
    public void onlyBackBecomesMeta() {
        int[] others = {KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_META_LEFT};
        for (int keyCode : others) {
            assertEquals("key " + keyCode, 0, KeyboardTranslator.backAsMetaKey(true, keyCode));
        }
    }

    @Test
    public void keysAreRemappedOnlyWhenForcedAndFromARealDevice() {
        assertTrue(KeyboardTranslator.remapsToQwerty(true, 7));
        assertTrue("device 0 is a valid id", KeyboardTranslator.remapsToQwerty(true, 0));
        assertFalse("turned off", KeyboardTranslator.remapsToQwerty(false, 7));
        // -1 means the key didn't come from a device, like the on-screen keyboard's
        assertFalse(KeyboardTranslator.remapsToQwerty(true, -1));
        assertFalse(KeyboardTranslator.remapsToQwerty(false, -1));
    }
}
