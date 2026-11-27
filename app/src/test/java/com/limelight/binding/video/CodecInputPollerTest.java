package com.limelight.binding.video;

import static org.junit.Assert.*;
import org.junit.Test;

public class CodecInputPollerTest {
    @Test public void prefetchNeverWaitsForTheNextFrame() {
        int[] calls = {0};
        CodecInputPoller p = new CodecInputPoller(timeout -> {
            assertEquals(0, timeout);
            return ++calls[0] == 1 ? -1 : 4;
        }, () -> true, () -> 0L);
        assertEquals(-1, p.next(true, 0));
        assertEquals(1, calls[0]);
        assertEquals(4, p.next(true, 0));
    }

    @Test public void requiredInputRetriesAndReturnsTheAvailableIndex() {
        int[] calls = {0};
        CodecInputPoller p = new CodecInputPoller(timeout -> {
            assertEquals(10000, timeout);
            return ++calls[0] == 3 ? 2 : -1;
        }, () -> true, () -> 1L);
        assertEquals(2, p.next(false, 0));
    }

    @Test public void recoveryBreaksAnOutstandingRequiredWait() {
        boolean[] running = {true};
        CodecInputPoller p = new CodecInputPoller(timeout -> {
            assertTrue(running[0]);
            running[0] = false;
            return -1;
        }, () -> running[0], () -> 1L);
        assertEquals(-1, p.next(false, 0));
    }

    @Test public void hangDeadlineIsReachableWhenTheCodecKeepsReturningTryAgain() {
        long[] ms = {0};
        CodecInputPoller p = new CodecInputPoller(timeout -> {
            ms[0] += timeout / 1000;
            assertTrue(ms[0] <= 5000);
            return -1;
        }, () -> true, () -> ms[0]);
        assertEquals(-1, p.next(false, 0));
        assertEquals(5000, ms[0]);
    }
}
