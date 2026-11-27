package com.limelight.binding.video;

import org.junit.Test;
import static org.junit.Assert.*;

public class VideoStatsSampleTest {
    @Test public void unmeasuredFramesDoNotLowerTheMediaCodecMean() {
        VideoStats window = new VideoStats();
        window.totalFramesReceived = 10;
        window.decoderSamples = 6;
        window.decoderTimeMs = 60;
        window.decoderPeakNs = 12_000_000;
        window.minHostProcessingLatency = 40;
        VideoStats total = new VideoStats();
        total.copy(window);
        window.decoderPeakNs = 11_000_000;
        total.add(window);
        window.clear();
        assertEquals(0, window.decoderPeakNs);
        assertEquals(12_000_000, total.decoderPeakNs);
        total.add(window);
        assertEquals(10f, total.averageDecodeMs(false), 0f);
        assertEquals(6f, total.averageDecodeMs(true), 0f);
        assertEquals(40, total.minHostProcessingLatency);
        assertEquals(0f, window.averageDecodeMs(false), 0f);
    }
}
