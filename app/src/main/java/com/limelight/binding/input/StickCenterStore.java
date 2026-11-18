package com.limelight.binding.input;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Stores per-controller analog stick rest-center offsets and re-centers/rescales axis samples
 * while keeping full [-1, +1] travel in every direction.
 */
public final class StickCenterStore {
    public static final String PREF_NAME = "stick_center_calibration";
    public static final float MAX_OFFSET = 0.25f;

    public static final class Calibration {
        public final float lx;
        public final float ly;
        public final float rx;
        public final float ry;

        public Calibration(float lx, float ly, float rx, float ry) {
            this.lx = clampOffset(lx);
            this.ly = clampOffset(ly);
            this.rx = clampOffset(rx);
            this.ry = clampOffset(ry);
        }

        public static Calibration identity() {
            return new Calibration(0f, 0f, 0f, 0f);
        }
    }

    private final SharedPreferences prefs;

    public StickCenterStore(Context context) {
        this.prefs = context != null
                ? context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                : null;
    }

    private static String deviceKey(int vendorId, int productId, String descriptor) {
        String safeDesc = descriptor != null ? descriptor : "";
        return vendorId + ":" + productId + ":" + safeDesc;
    }

    public Calibration load(int vendorId, int productId, String descriptor) {
        if (prefs == null) {
            return Calibration.identity();
        }
        String base = deviceKey(vendorId, productId, descriptor);
        return new Calibration(
                prefs.getFloat(base + ".lx", 0f),
                prefs.getFloat(base + ".ly", 0f),
                prefs.getFloat(base + ".rx", 0f),
                prefs.getFloat(base + ".ry", 0f)
        );
    }

    public void save(int vendorId, int productId, String descriptor,
                     float lxCenter, float lyCenter, float rxCenter, float ryCenter) {
        if (prefs == null) {
            return;
        }
        String base = deviceKey(vendorId, productId, descriptor);
        prefs.edit()
                .putFloat(base + ".lx", clampOffset(lxCenter))
                .putFloat(base + ".ly", clampOffset(lyCenter))
                .putFloat(base + ".rx", clampOffset(rxCenter))
                .putFloat(base + ".ry", clampOffset(ryCenter))
                .apply();
    }

    public void clear(int vendorId, int productId, String descriptor) {
        if (prefs == null) {
            return;
        }
        String base = deviceKey(vendorId, productId, descriptor);
        prefs.edit()
                .remove(base + ".lx")
                .remove(base + ".ly")
                .remove(base + ".rx")
                .remove(base + ".ry")
                .apply();
    }

    public static float clampOffset(float center) {
        if (Float.isNaN(center) || Float.isInfinite(center)) {
            return 0f;
        }
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, center));
    }

    public static float applyCenterOffset(float raw, float center) {
        if (Float.isNaN(raw)) {
            return 0f;
        }
        float clampedCenter = clampOffset(center);
        if (Math.abs(clampedCenter) < 1e-4f) {
            return Math.max(-1f, Math.min(1f, raw));
        }
        float shifted = raw - clampedCenter;
        if (shifted >= 0f) {
            float span = Math.max(0.1f, 1f - clampedCenter);
            return Math.min(1f, shifted / span);
        }
        float span = Math.max(0.1f, 1f + clampedCenter);
        return Math.max(-1f, shifted / span);
    }
}
