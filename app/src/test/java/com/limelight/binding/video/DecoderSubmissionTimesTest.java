package com.limelight.binding.video;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class DecoderSubmissionTimesTest {
    private final DecoderSubmissionTimes times = new DecoderSubmissionTimes();
    private final DecoderSubmissionTimes.Submit accept = (pts, flags) -> {};

    @Test public void outputCanArriveInsideTheSubmitCall() {
        times.submit(100, 0, 2_000_000, (pts, flags) ->
                assertEquals(6_000_000, times.takeElapsedNs(pts, 8_000_000)));
        assertEquals(0, times.size());
    }

    @Test public void rejectedInputLeavesNoWatchdogEntry() {
        assertThrows(IllegalStateException.class, () -> times.submit(100, 0, 10, (p, f) -> {
            throw new IllegalStateException();
        }));
        assertEquals(0, times.size());
    }

    @Test public void replacingATimestampAtCapacityKeepsTheOldestPendingFrame() {
        for (int i = 0; i < 128; i++) times.submit(i, 0, 10, accept);
        times.submit(127, 0, 16, accept);
        assertEquals(128, times.size());
        assertEquals(10, times.takeElapsedNs(0, 20));
        assertEquals(4, times.takeElapsedNs(127, 20));
    }

    @Test public void backlogDropsAndFlushKeepOnlyRelevantEntries() {
        for (int i = 0; i < 140; i++) times.submit(i, 0, 10, accept);
        assertEquals(128, times.size());
        assertEquals(-1, times.takeElapsedNs(0, 20));
        times.discard(15, false);
        assertEquals(10, times.takeElapsedNs(12, 20));
        times.discardBefore(139);
        assertEquals(1, times.size());
        times.clear();
        assertEquals(-1, times.takeElapsedNs(139, 20));
    }
}
