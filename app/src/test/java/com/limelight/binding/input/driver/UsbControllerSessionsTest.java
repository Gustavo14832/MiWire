package com.limelight.binding.input.driver;

import java.util.ArrayDeque;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class UsbControllerSessionsTest {
    private final ArrayDeque<Runnable> worker = new ArrayDeque<>();
    private final UsbControllerSessions sessions = new UsbControllerSessions(worker::add);
    private static class Pad extends AbstractController {
        int stops;
        Pad() { super(12, null, 0, 0); }
        public boolean start() { return true; }
        public void stop() { stops++; }
        public void rumble(short a, short b) {}
        public void rumbleTriggers(short a, short b) {}
    }
    @Test public void duplicateNotificationsOpenOnceOnTheWorker() {
        Pad pad = new Pad();
        sessions.attach("port", () -> pad);
        sessions.attach("port", () -> {throw new AssertionError("duplicate");});
        assertEquals(1, worker.size());
        assertTrue(sessions.active().isEmpty());
        worker.remove().run();
        assertEquals(Collections.singletonList(pad), sessions.active());
        sessions.detach("port");
        sessions.stopAll();
        worker.remove().run();
        assertEquals(1, pad.stops);
    }
    @Test public void removalDuringSetupClosesOldDeviceBeforeReusingAddress() {
        Pad old = new Pad(), replacement = new Pad();
        sessions.attach("port", () -> {
            sessions.detach("port");
            sessions.attach("port", () -> {assertEquals(1, old.stops); return replacement;});
            return old;
        });
        worker.remove().run();
        worker.remove().run();
        sessions.removed(old);
        assertEquals(Collections.singletonList(replacement), sessions.active());
    }
    @Test public void closedOrFailedAttemptsDoNotBlockFutureAttachments() {
        sessions.attach("x", () -> {throw new AssertionError("stale open");});
        sessions.stopAll();
        worker.remove().run();
        sessions.attach("x", () -> null);
        worker.remove().run();
        Pad pad = new Pad();
        sessions.attach("x", () -> pad);
        worker.remove().run();
        sessions.removed(pad);
        assertTrue(sessions.active().isEmpty());
    }
}
