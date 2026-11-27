package com.limelight.binding.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkRateSampleTest {
    private final NetworkRateSample sample = new NetworkRateSample();

    @Test public void measuresBytesInBothDirectionsOverActualElapsedTime() {
        assertEquals(-1f, sample.update(0, 0, 100), 0);
        assertEquals(35f, sample.update(8_000_000, 750_000, 2100), 0.0001f);
        assertEquals(35f, sample.update(12_000_000, 1_125_000, 3100), 0.0001f);
        assertEquals(0f, sample.update(12_000_000, 1_125_000, 4100), 0);
    }

    @Test public void skipsUnavailableAndRestartedCountersAndResetsAfterHiding() {
        sample.update(100, 200, 0);
        assertEquals(-1f, sample.update(-1, 210, 100), 0);
        assertEquals(-1f, sample.update(200, 250, 200), 0);
        assertEquals(-1f, sample.update(150, 250, 300), 0);
        assertEquals(-1f, sample.update(170, 5, 400), 0);
        assertEquals(-1f, sample.update(200, 6, 400), 0);
        sample.reset();
        assertEquals(-1f, sample.update(300, 9, 1000), 0);
        assertEquals(8f, sample.update(1_000_300, 9, 2000), 0);
    }
}
