package com.limelight;

import org.junit.Test;
import static org.junit.Assert.*;

public class MouseDispatchStatsTest {
    private final MouseDispatchStats stats = new MouseDispatchStats();

    @Test public void p95UsesNewestAndOldestSamplesFromTheSameBatch() {
        for (int i = 0; i < 19; i++) stats.record(100, 98, 90);
        stats.record(150, 80, 40);
        assertEquals("2", stats.newestP95());
        assertEquals("10", stats.oldestP95());
        assertEquals(20, stats.batchCount());
    }

    @Test public void overflowIsExplicitAndAnIdleIntervalStartsAFreshWindow() {
        stats.record(900, 772, 300);
        assertEquals("≥128", stats.newestP95());
        assertEquals("≥128", stats.oldestP95());
        assertTrue(stats.fresh(2900));
        assertFalse(stats.fresh(2901));
        stats.record(3000, 3000, 2998);
        assertEquals(1, stats.batchCount());
        assertEquals("0", stats.newestP95());
        assertEquals("2", stats.oldestP95());
    }

    @Test public void invalidTimestampsAndClearedSamplesAreNotReported() {
        stats.record(100, 101, 90);
        stats.record(100, 98, 99);
        stats.record(100, 98, -1);
        assertFalse(stats.fresh(100));
        stats.record(100, 98, 90);
        stats.clear();
        assertEquals(0, stats.batchCount());
        assertFalse(stats.fresh(100));
    }
}
