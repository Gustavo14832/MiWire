package com.limelight.binding.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class PcmWriterTest {
    @Test public void resumesAtFirstUnwrittenSample() {
        int[] calls = {0};
        PcmWriter<short[]> writer = (samples, offset, count) -> {
            assertEquals(calls[0]++ * 240, offset);
            assertEquals(samples.length - offset, count);
            return 240;
        };
        assertEquals(480, writer.writeAll(new short[480], 480));
        assertEquals(2, calls[0]);
    }

    @Test public void stopsAfterPartialWriteFollowedByZeroOrError() {
        for (int status : new int[]{0, -6}) {
            int[] calls = {0};
            PcmWriter<float[]> writer = (samples, offset, count) -> {
                assertTrue(++calls[0] <= 2);
                return offset == 0 ? 240 : status;
            };
            assertEquals(status, writer.writeAll(new float[480], 480));
            assertEquals(2, calls[0]);
        }
    }
}
