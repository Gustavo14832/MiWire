package com.limelight.binding.video;

import android.content.Context;
import android.os.Build;
import android.os.PerformanceHintManager;
import android.os.Process;

import com.limelight.LimeLog;

/**
 * Wraps Android's ADPF PerformanceHintManager (API 31+) over all CPU threads in the
 * MediaCodec decode/render pipeline and reports actual CPU work rather than hardware VPU waits.
 * Adapted from LavArtemis (Lavagnou).
 */
public final class AdpfHelper {
    public static final long DEFAULT_CPU_TARGET_NS = 3_000_000L;
    public static final long MIN_REPORT_WORK_NS = 100_000L;

    private final Object lock = new Object();
    private final Context context;
    private final long targetWorkDurationNs;

    private PerformanceHintManager.Session session;
    private boolean unsupported;
    private volatile int[] tids = new int[0];
    private boolean tidsDirty;

    public AdpfHelper(Context context, long targetWorkDurationNs) {
        this.context = context;
        this.targetWorkDurationNs = Math.max(MIN_REPORT_WORK_NS, targetWorkDurationNs);
        this.unsupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null;
    }

    public static long clampWorkDurationNs(long actualWorkDurationNs, long targetWorkDurationNs) {
        if (actualWorkDurationNs <= 0L) {
            return 0L;
        }
        long maxClamped = Math.max(MIN_REPORT_WORK_NS, targetWorkDurationNs * 4L);
        return Math.max(MIN_REPORT_WORK_NS, Math.min(actualWorkDurationNs, maxClamped));
    }

    public static int[] appendUniqueTid(int[] existing, int tid) {
        if (tid <= 0) {
            return existing;
        }
        for (int current : existing) {
            if (current == tid) {
                return existing;
            }
        }
        int[] updated = new int[existing.length + 1];
        System.arraycopy(existing, 0, updated, 0, existing.length);
        updated[existing.length] = tid;
        return updated;
    }

    public void registerCurrentThread() {
        if (unsupported) {
            return;
        }
        int tid = Process.myTid();
        for (int existing : tids) {
            if (existing == tid) {
                return;
            }
        }
        synchronized (lock) {
            if (unsupported) {
                return;
            }
            int[] updated = appendUniqueTid(tids, tid);
            if (updated != tids) {
                tids = updated;
                tidsDirty = true;
            }
        }
    }

    public void reportFrameWorkDuration(long actualWorkDurationNs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || unsupported) {
            return;
        }
        long clamped = clampWorkDurationNs(actualWorkDurationNs, targetWorkDurationNs);
        if (clamped <= 0L) {
            return;
        }
        synchronized (lock) {
            if (unsupported) {
                return;
            }
            try {
                if (tidsDirty) {
                    if (session != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                            && tids.length > 0) {
                        try {
                            session.setThreads(tids);
                            tidsDirty = false;
                        } catch (RuntimeException e) {
                            LimeLog.warning("ADPF setThreads failed, recreating session: " + e);
                        }
                    }
                }
                if (tidsDirty) {
                    closeSessionLocked();
                    if (tids.length > 0) {
                        PerformanceHintManager phm = context.getSystemService(PerformanceHintManager.class);
                        if (phm == null) {
                            unsupported = true;
                            return;
                        }
                        session = phm.createHintSession(tids, targetWorkDurationNs);
                        if (session == null) {
                            unsupported = true;
                            return;
                        }
                        LimeLog.info("ADPF hint session created for " + tids.length +
                                " threads (target " + (targetWorkDurationNs / 1_000_000L) + " ms)");
                    }
                    tidsDirty = false;
                }
                if (session != null) {
                    session.reportActualWorkDuration(clamped);
                }
            } catch (Throwable t) {
                LimeLog.warning("ADPF reporting failed, disabling: " + t);
                unsupported = true;
                closeSessionLocked();
            }
        }
    }

    public void close() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        synchronized (lock) {
            closeSessionLocked();
            unsupported = true;
        }
    }

    private void closeSessionLocked() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (session != null) {
            try {
                session.close();
            } catch (Throwable ignored) {
            }
            session = null;
        }
    }
}
