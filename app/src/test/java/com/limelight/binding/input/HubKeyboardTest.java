package com.limelight.binding.input;

import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowInputDevice;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = HubKeyboardTest.Keys.class)
public class HubKeyboardTest {
    @Implements(InputDevice.class)
    public static class Keys extends ShadowInputDevice {
        static InputDevice board;
        static int calls;
        static boolean swapped;
        @Implementation protected static int[] getDeviceIds() { return new int[]{7}; }
        @Implementation protected static InputDevice getDevice(int id) { return id == 7 ? board : null; }
        @Implementation protected int getKeyboardType() { return InputDevice.KEYBOARD_TYPE_ALPHABETIC; }
        @Implementation protected int getKeyCodeForKeyLocation(int key) {
            calls++;
            if (swapped && key == KeyEvent.KEYCODE_A) return KeyEvent.KEYCODE_B;
            if (swapped && key == KeyEvent.KEYCODE_B) return KeyEvent.KEYCODE_A;
            return key;
        }
    }
    @Before public void setup() {
        Keys.board = ShadowInputDevice.makeInputDeviceNamed("Keyboard");
        Keys.calls = 0;
        Keys.swapped = false;
    }

    @Test public void changedBurstRebuildsOneTableAndKeepsTheNewLayout() {
        KeyboardTranslator keys = new KeyboardTranslator();
        Keys.calls = 0;
        Keys.swapped = true;
        for (int i = 0; i < 24; i++) keys.onInputDeviceChanged(7);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(KeyEvent.getMaxKeyCode() + 1, Keys.calls);
        assertEquals((short) 0x8041, keys.translate(KeyEvent.KEYCODE_B, 7));
        System.out.println("Android API 34, 24 notifications: " + Keys.calls + " key-location lookups");
    }

    @Test public void anEarlyKeySeesTheLatestLayoutWithoutASecondBuild() {
        KeyboardTranslator keys = new KeyboardTranslator();
        Keys.swapped = true;
        Keys.calls = 0;
        keys.onInputDeviceChanged(7);
        assertTrue(keys.hasNormalizedMapping(KeyEvent.KEYCODE_B, 7));
        assertEquals((short) 0x8041, keys.translate(KeyEvent.KEYCODE_B, 7));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(KeyEvent.getMaxKeyCode() + 1, Keys.calls);
    }

    @Test public void disabledOrUnpluggedKeyboardDoesNoLayoutWork() {
        KeyboardTranslator keys = new KeyboardTranslator(false);
        keys.onInputDeviceAdded(7);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, Keys.calls);
        keys.setForceQwerty(true);
        assertTrue(keys.hasNormalizedMapping(KeyEvent.KEYCODE_A, 7));
        Keys.calls = 0;
        keys.onInputDeviceChanged(7);
        keys.onInputDeviceRemoved(7);
        assertFalse(keys.hasNormalizedMapping(KeyEvent.KEYCODE_A, 7));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, Keys.calls);
    }
}
