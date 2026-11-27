package com.limelight.binding.input.driver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** One initialization per attachment. USB operations run on the service's serial worker. */
final class UsbControllerSessions {
    private static final class Attachment {
        AbstractController controller;
    }
    private final HashMap<String, Attachment> attached = new HashMap<>();
    private final Executor worker;

    UsbControllerSessions(Executor worker) {
        this.worker = worker;
    }

    synchronized void attach(String name, Supplier<AbstractController> open) {
        if (attached.containsKey(name)) return;
        Attachment ticket = new Attachment();
        attached.put(name, ticket);
        worker.execute(() -> {
            synchronized (this) {
                if (attached.get(name) != ticket) return;
            }
            AbstractController controller = null;
            try {
                controller = open.get();
            } finally {
                synchronized (this) {
                    if (attached.get(name) == ticket) {
                        if (controller == null) attached.remove(name);
                        ticket.controller = controller;
                        controller = null;
                    }
                }
                // The hub disappeared while open/start was running. Return the old device
                // before the next queued initialization can claim its interfaces.
                if (controller != null) controller.stop();
            }
        });
    }

    synchronized void detach(String name) {
        Attachment ticket = attached.remove(name);
        if (ticket != null && ticket.controller != null) worker.execute(ticket.controller::stop);
    }

    synchronized void stopAll() {
        for (String name : new ArrayList<>(attached.keySet())) detach(name);
    }

    synchronized void removed(AbstractController controller) {
        attached.values().removeIf(ticket -> ticket.controller == controller);
    }

    synchronized ArrayList<AbstractController> active() {
        ArrayList<AbstractController> result = new ArrayList<>();
        for (Attachment ticket : attached.values()) {
            if (ticket.controller != null) result.add(ticket.controller);
        }
        return result;
    }
}
