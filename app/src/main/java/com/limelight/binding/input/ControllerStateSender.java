package com.limelight.binding.input;

import com.limelight.nvstream.NvConnection;

/**
 * Deduplicates consecutive identical controller state reports (common on 2.4 GHz USB dongles and
 * repeated polling) while preserving a 1-second keepalive heartbeat and 0-ns immediate dispatch
 * on any button, trigger, stick, or mask change.
 */
public final class ControllerStateSender {
    public static final int MAX_CONTROLLERS = 16;
    public static final long KEEPALIVE_INTERVAL_NS = 1_000_000_000L;

    private final boolean[] initialized = new boolean[MAX_CONTROLLERS];
    private final short[] lastActiveMask = new short[MAX_CONTROLLERS];
    private final int[] lastButtonFlags = new int[MAX_CONTROLLERS];
    private final byte[] lastLeftTrigger = new byte[MAX_CONTROLLERS];
    private final byte[] lastRightTrigger = new byte[MAX_CONTROLLERS];
    private final short[] lastLeftStickX = new short[MAX_CONTROLLERS];
    private final short[] lastLeftStickY = new short[MAX_CONTROLLERS];
    private final short[] lastRightStickX = new short[MAX_CONTROLLERS];
    private final short[] lastRightStickY = new short[MAX_CONTROLLERS];
    private final long[] lastSentNs = new long[MAX_CONTROLLERS];

    public boolean sendIfNeeded(NvConnection conn,
                                short controllerNumber,
                                short activeGamepadMask,
                                int buttonFlags,
                                byte leftTrigger,
                                byte rightTrigger,
                                short leftStickX,
                                short leftStickY,
                                short rightStickX,
                                short rightStickY,
                                long nowNs,
                                boolean force) {
        int slot = controllerNumber;
        if (!force && slot >= 0 && slot < MAX_CONTROLLERS && initialized[slot]) {
            boolean unchanged = lastActiveMask[slot] == activeGamepadMask
                    && lastButtonFlags[slot] == buttonFlags
                    && lastLeftTrigger[slot] == leftTrigger
                    && lastRightTrigger[slot] == rightTrigger
                    && lastLeftStickX[slot] == leftStickX
                    && lastLeftStickY[slot] == leftStickY
                    && lastRightStickX[slot] == rightStickX
                    && lastRightStickY[slot] == rightStickY;
            if (unchanged && (nowNs - lastSentNs[slot]) < KEEPALIVE_INTERVAL_NS) {
                return false;
            }
        }

        if (slot >= 0 && slot < MAX_CONTROLLERS) {
            initialized[slot] = true;
            lastActiveMask[slot] = activeGamepadMask;
            lastButtonFlags[slot] = buttonFlags;
            lastLeftTrigger[slot] = leftTrigger;
            lastRightTrigger[slot] = rightTrigger;
            lastLeftStickX[slot] = leftStickX;
            lastLeftStickY[slot] = leftStickY;
            lastRightStickX[slot] = rightStickX;
            lastRightStickY[slot] = rightStickY;
            lastSentNs[slot] = nowNs;
        }

        conn.sendControllerInput(controllerNumber, activeGamepadMask, buttonFlags,
                leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
        return true;
    }

    public void resetController(int controllerNumber) {
        if (controllerNumber >= 0 && controllerNumber < MAX_CONTROLLERS) {
            initialized[controllerNumber] = false;
            lastSentNs[controllerNumber] = 0L;
        }
    }

    public void resetAll() {
        for (int i = 0; i < MAX_CONTROLLERS; i++) {
            initialized[i] = false;
            lastSentNs[i] = 0L;
        }
    }
}
