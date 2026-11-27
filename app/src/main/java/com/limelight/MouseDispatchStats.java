package com.limelight;

import java.util.Arrays;

/** Android event time to entry into the stream view, in whole milliseconds. UI thread only. */
final class MouseDispatchStats {
    static final int OVERFLOW_MS = 128;
    private final int[] newest = new int[OVERFLOW_MS + 1];
    private final int[] oldest = new int[OVERFLOW_MS + 1];
    private int batches;
    private long lastArrival;

    void record(long now, long latest, long first) {
        if (latest > now || first > latest || first < 0) return;
        if (batches != 0 && now - lastArrival > 2000) clear();
        newest[(int)Math.min(now - latest, OVERFLOW_MS)]++;
        oldest[(int)Math.min(now - first, OVERFLOW_MS)]++;
        batches++;
        lastArrival = now;
    }

    boolean fresh(long now) { return batches > 0 && now - lastArrival <= 2000; }

    String newestP95() { return p95(newest); }
    String oldestP95() { return p95(oldest); }
    int batchCount() { return batches; }

    private String p95(int[] histogram) {
        // Nearest rank: ceil(0.95 * n) = n - floor(n / 20).
        int rank = batches - batches / 20;
        for (int ms = 0; ms < OVERFLOW_MS; ms++) {
            rank -= histogram[ms];
            if (rank <= 0) return Integer.toString(ms);
        }
        return "≥" + OVERFLOW_MS;
    }

    void clear() {
        if (batches == 0) return;
        Arrays.fill(newest, 0);
        Arrays.fill(oldest, 0);
        batches = 0;
    }
}
