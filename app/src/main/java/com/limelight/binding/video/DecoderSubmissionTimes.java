package com.limelight.binding.video;

import java.util.Arrays;

/** One producer, one consumer. The bounded table also protects the decoder statistics. */
final class DecoderSubmissionTimes {
    interface Submit { void queue(long ptsUs, int flags); }

    private final long[] pts = new long[128];
    private final long[] started = new long[pts.length];
    private int count;

    private synchronized void put(long timestamp, long startNs) {
        if (count == pts.length && Arrays.binarySearch(pts, 0, count, timestamp) < 0) {
            removeRange(0, 1);
        }
        int at = count;
        if (count != 0 && timestamp <= pts[count - 1]) {
            int found = Arrays.binarySearch(pts, 0, count, timestamp);
            if (found >= 0) {
                started[found] = startNs;
                return;
            }
            at = ~found;
            System.arraycopy(pts, at, pts, at + 1, count - at);
            System.arraycopy(started, at, started, at + 1, count - at);
        }
        pts[at] = timestamp;
        started[at] = startNs;
        count++;
    }

    void submit(long ptsUs, int flags, long startNs, Submit submit) {
        put(ptsUs, startNs);
        // The consumer can observe this frame before queueInputBuffer returns.
        try {
            submit.queue(ptsUs, flags);
        } catch (RuntimeException e) {
            discard(ptsUs, false);
            throw e;
        }
    }

    synchronized long takeElapsedNs(long ptsUs, long readyNs) {
        int at = Arrays.binarySearch(pts, 0, count, ptsUs);
        long elapsed = at >= 0 ? readyNs - started[at] : -1L;
        removeRange(0, at >= 0 ? at + 1 : ~at);
        return elapsed;
    }

    synchronized void discard(long ptsUs, boolean includeOlder) {
        int at = Arrays.binarySearch(pts, 0, count, ptsUs);
        if (at >= 0) removeRange(includeOlder ? 0 : at, at + 1);
        else if (includeOlder) removeRange(0, ~at);
    }

    synchronized void discardBefore(long ptsUs) {
        int at = Arrays.binarySearch(pts, 0, count, ptsUs);
        removeRange(0, at >= 0 ? at : ~at);
    }

    // Both endpoints are indices. The hot path consumes the sole entry without copying.
    private void removeRange(int first, int end) {
        if (first == end) return;
        if (end < count) {
            System.arraycopy(pts, end, pts, first, count - end);
            System.arraycopy(started, end, started, first, count - end);
        }
        count -= end - first;
    }

    synchronized void clear() { count = 0; }
    synchronized int size() { return count; }
}
