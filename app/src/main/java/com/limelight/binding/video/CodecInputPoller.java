package com.limelight.binding.video;

import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

final class CodecInputPoller {
    static final int HANG_TIMEOUT_MS = 5000;
    private final IntUnaryOperator dequeue;
    private final BooleanSupplier canPoll;
    private final LongSupplier uptimeMs;

    CodecInputPoller(IntUnaryOperator dequeue, BooleanSupplier canPoll, LongSupplier uptimeMs) {
        this.dequeue = dequeue;
        this.canPoll = canPoll;
        this.uptimeMs = uptimeMs;
    }

    int next(boolean prefetch, long startedMs) {
        while (canPoll.getAsBoolean()) {
            int index = dequeue.applyAsInt(prefetch ? 0 : 10_000);
            if (index >= 0 || prefetch || uptimeMs.getAsLong() - startedMs >= HANG_TIMEOUT_MS) {
                return index;
            }
        }
        return -1;
    }
}
