package com.limelight;

import org.junit.Test;
import static org.junit.Assert.*;

public class FullHdTvModeTest {
    @Test public void upgradesOnlyAnUndersizedTvOutputForFullHd60() {
        assertTrue(FullHdTvMode.needsUpgrade(true, 1920, 1080, 60, 1280, 720));
        assertFalse(FullHdTvMode.needsUpgrade(true, 1920, 1080, 60, 1920, 1080));
        assertFalse(FullHdTvMode.needsUpgrade(true, 1920, 1080, 60, 3840, 2160));
        assertFalse(FullHdTvMode.needsUpgrade(false, 1920, 1080, 60, 1280, 720));
        assertFalse(FullHdTvMode.needsUpgrade(true, 1280, 720, 60, 1280, 720));
        assertFalse(FullHdTvMode.needsUpgrade(true, 1920, 1080, 30, 1280, 720));
    }

    @Test public void selectsTheClosestAdvertisedFullHd60Timing() {
        assertTrue(FullHdTvMode.isBetter(1920, 1080, 59.94f, 0));
        assertTrue(FullHdTvMode.isBetter(1920, 1080, 60, 59.94f));
        assertFalse(FullHdTvMode.isBetter(1920, 1080, 59.94f, 60));
        assertFalse(FullHdTvMode.isBetter(1920, 1080, 60, 60));
    }

    @Test public void rejectsNonMatchingTimingsAndSizes() {
        for (float hz : new float[] {24, 30, 50, 75}) {
            assertFalse(FullHdTvMode.isBetter(1920, 1080, hz, 0));
        }
        assertFalse(FullHdTvMode.isBetter(1280, 720, 60, 0));
        assertFalse(FullHdTvMode.isBetter(3840, 2160, 60, 0));
    }
}
