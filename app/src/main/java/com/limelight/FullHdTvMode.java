package com.limelight;

final class FullHdTvMode {
    private FullHdTvMode() {}

    static boolean needsUpgrade(boolean tv, int streamWidth, int streamHeight, int fps, int width, int height) {
        return tv && streamWidth == 1920 && streamHeight == 1080 && fps == 60 &&
                width <= 1920 && height <= 1080 && (width < 1920 || height < 1080);
    }

    // Accept both NTSC fractional and integer 60 Hz timings. Prefer the closest to the stream.
    static boolean isBetter(int width, int height, float hz, float bestHz) {
        float error = Math.abs(hz - 60f);
        return width == 1920 && height == 1080 && error <= 0.1f && error < Math.abs(bestHz - 60f);
    }
}
