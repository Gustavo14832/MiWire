package com.limelight.binding.video;

import android.util.LongSparseLongArray;

/** One input producer, one output consumer. The bounded table is also their statistics lock. */
final class DecoderSubmissionTimes {
    interface Submit { void queue(long ptsUs, int flags); }

    private final LongSparseLongArray pending = new LongSparseLongArray(128);

    void submit(long ptsUs, int flags, long startNs, Submit submit) {
        synchronized (this) {
            if (pending.size() == 128 && pending.indexOfKey(ptsUs) < 0) pending.removeAt(0);
            pending.append(ptsUs, startNs);
        }
        // The output thread can observe this frame before queueInputBuffer returns.
        try {
            submit.queue(ptsUs, flags);
        } catch (RuntimeException e) {
            synchronized (this) { pending.delete(ptsUs); }
            throw e;
        }
    }

    synchronized long takeElapsedNs(long ptsUs, long readyNs) {
        int index = pending.indexOfKey(ptsUs);
        long elapsedNs = index < 0 ? -1L : readyNs - pending.valueAt(index);
        discard(ptsUs, true);
        return elapsedNs;
    }

    synchronized void discard(long ptsUs, boolean includeOlder) {
        if (includeOlder) discardBefore(ptsUs);
        pending.delete(ptsUs);
    }

    synchronized void discardBefore(long ptsUs) {
        int end = 0;
        int count = pending.size();
        while (end < count && pending.keyAt(end) < ptsUs) end++;
        for (int i = end - 1; i >= 0; i--) pending.removeAt(i);
    }

    synchronized void clear() { pending.clear(); }
    synchronized int size() { return pending.size(); }
}
