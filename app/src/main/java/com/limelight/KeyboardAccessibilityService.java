package com.limelight;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Passes physical keyboard shortcuts that Android would handle itself (Alt+Tab, Win and so on)
 * to the stream, so they reach the PC. It only reads key events while a stream is connected.
 * Ported from LavArtemis by Lavagnou.
 */
public class KeyboardAccessibilityService extends AccessibilityService {

    // Keys that stay with Android
    private static final List<Integer> PASS_THROUGH_KEYS = Arrays.asList(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_POWER
    );

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();

        Game game = Game.instance;
        if (game != null && game.isConnected() && !PASS_THROUGH_KEYS.contains(keyCode)) {
            // Some Xiaomi tablets report the Esc key as Back (scan code 1), so send Esc instead
            boolean escAsBack = event.getScanCode() == 1;

            if (action == KeyEvent.ACTION_DOWN) {
                game.handleKeyDown(escAsBack ? new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE) : event);
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                game.handleKeyUp(escAsBack ? new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE) : event);
                return true;
            }
        }

        return super.onKeyEvent(event);
    }

    @Override
    public void onServiceConnected() {
        LimeLog.info("Keyboard service is connected");
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.packageNames = new String[] { BuildConfig.APPLICATION_ID };
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override
    public void onInterrupt() {
    }
}
