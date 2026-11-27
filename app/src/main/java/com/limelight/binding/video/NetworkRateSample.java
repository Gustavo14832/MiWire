package com.limelight.binding.video;

final class NetworkRateSample {
    private long lastRx = -1;
    private long lastTx;
    private long lastMs;

    float update(long rx, long tx, long nowMs) {
        if (rx < 0 || tx < 0) {
            reset();
            return -1;
        }
        float mbps = -1;
        if (lastRx >= 0 && rx >= lastRx && tx >= lastTx && nowMs > lastMs) {
            double bytes = (double) (rx - lastRx) + (tx - lastTx);
            mbps = (float) (bytes * 0.008 / (nowMs - lastMs));
        }
        lastRx = rx;
        lastTx = tx;
        lastMs = nowMs;
        return mbps;
    }

    void reset() {
        lastRx = -1;
    }
}
