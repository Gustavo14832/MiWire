package com.limelight.binding.video;

import org.junit.Test;
import static com.limelight.binding.video.DecoderStallWatchdog.Action.*;
import static org.junit.Assert.assertEquals;

public class DecoderStallWatchdogTest {
    private final DecoderStallWatchdog watchdog = new DecoderStallWatchdog();

    @Test public void escalatesAfterSuccessfulFlushWithNoNewOutput() {
        watchdog.onOutput(100);
        assertEquals(NONE, watchdog.poll(1299, 4, 120));
        assertEquals(REQUEST_IDR, watchdog.poll(1300, 4, 120));
        assertEquals(FLUSH, watchdog.poll(2500, 8, 2480));
        watchdog.onRecovery(true, 2600);
        assertEquals(NONE, watchdog.poll(3800, 3, 3700));
        assertEquals(NONE, watchdog.poll(3800, 4, 2480));
        assertEquals(RESTART, watchdog.poll(3800, 4, 3700));
        watchdog.onRecovery(false, 3900);
        assertEquals(REQUEST_IDR, watchdog.poll(5100, 4, 5010));
    }

    @Test public void outputRestoresFirstStepAndFullGracePeriod() {
        watchdog.onRecovery(true, 100);
        watchdog.onOutput(900);
        assertEquals(NONE, watchdog.poll(2099, 4, 1000));
        assertEquals(REQUEST_IDR, watchdog.poll(2100, 4, 1000));
    }
}
