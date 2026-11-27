package com.limelight.binding.video;

/** Accessed by the output thread, or while all codec threads are quiesced. */
final class DecoderStallWatchdog {
    enum Action { NONE, REQUEST_IDR, FLUSH, RESTART }
    private static final long TIMEOUT_MS = 1200;
    private Action next = Action.REQUEST_IDR;
    private long progressMs;
    private long lastActionMs;

    void onOutput(long nowMs) {
        next = Action.REQUEST_IDR;
        progressMs = lastActionMs = nowMs;
    }

    void onRecovery(boolean flushed, long nowMs) {
        // A flush discards all old buffer indices. Give new input a full grace
        // period, but keep the restart step if the flushed codec stays silent.
        next = flushed ? Action.RESTART : Action.REQUEST_IDR;
        progressMs = lastActionMs = nowMs;
    }

    Action poll(long nowMs, int pending, long lastInputMs) {
        if (pending < 4 || lastInputMs < progressMs || nowMs - lastActionMs < TIMEOUT_MS) {
            return Action.NONE;
        }
        Action action = next;
        next = action == Action.REQUEST_IDR ? Action.FLUSH :
                action == Action.FLUSH ? Action.RESTART : Action.REQUEST_IDR;
        lastActionMs = nowMs;
        return action;
    }
}
