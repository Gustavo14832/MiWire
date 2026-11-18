package com.limelight.binding.video;

import java.util.Arrays;

/**
 * Rolling window of present-to-present intervals used to measure pacing smoothness via
 * p50/p95/p99 percentiles (from LavArtemis, Lavagnou).
 */
public final class PacingStats {
    public static final int WINDOW_SIZE = 512;

    private final float[] deltasMs = new float[WINDOW_SIZE];
    private int writeIndex;
    private int count;
    private long lastPresentNs;
    private long lastHostPtsUs;

    public static final class Snapshot {
        public float p50Ms;
        public float p95Ms;
        public float p99Ms;
        public int sampleCount;
    }

    public synchronized void recordPresent(long presentTimeNs) {
        recordPresent(presentTimeNs, 0L, 0);
    }

    public synchronized void recordPresent(long presentTimeNs, long hostPtsUs, int targetFps) {
        if (presentTimeNs <= 0L) {
            return;
        }
        boolean hostIdlePause = hostPtsUs > 0L && lastHostPtsUs > 0L && targetFps > 0
                && (hostPtsUs - lastHostPtsUs) > (3_000_000L / targetFps);
        if (!hostIdlePause && lastPresentNs != 0L && presentTimeNs > lastPresentNs) {
            long deltaNs = presentTimeNs - lastPresentNs;
            if (deltaNs > 0L && deltaNs < 1_000_000_000L) {
                deltasMs[writeIndex] = deltaNs / 1_000_000.0f;
                writeIndex = (writeIndex + 1) % WINDOW_SIZE;
                if (count < WINDOW_SIZE) {
                    count++;
                }
            }
        }
        if (presentTimeNs > lastPresentNs) {
            lastPresentNs = presentTimeNs;
        }
        if (hostPtsUs > 0L && hostPtsUs > lastHostPtsUs) {
            lastHostPtsUs = hostPtsUs;
        }
    }

    public synchronized Snapshot snapshot() {
        Snapshot snap = new Snapshot();
        snap.sampleCount = count;
        if (count == 0) {
            return snap;
        }
        float[] sorted = new float[count];
        System.arraycopy(deltasMs, 0, sorted, 0, count);
        Arrays.sort(sorted);
        snap.p50Ms = sorted[(int) (count * 0.50f)];
        snap.p95Ms = sorted[Math.min(count - 1, (int) (count * 0.95f))];
        snap.p99Ms = sorted[Math.min(count - 1, (int) (count * 0.99f))];
        return snap;
    }

    public synchronized void reset() {
        writeIndex = 0;
        count = 0;
        lastPresentNs = 0L;
        lastHostPtsUs = 0L;
    }
}
