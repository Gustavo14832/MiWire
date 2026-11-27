package com.limelight;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** All state changes, including close(), run on the activity thread. */
final class StreamNetworkCallback extends ConnectivityManager.NetworkCallback {
    private final Executor main;
    private final Consumer<NetworkCapabilities> update;
    private final Function<Network, NetworkCapabilities> legacyLookup;
    private Network current;
    private boolean closed;

    StreamNetworkCallback(Network initial, Executor main, Consumer<NetworkCapabilities> update,
                          Function<Network, NetworkCapabilities> legacyLookup) {
        this.current = initial;
        this.main = main;
        this.update = update;
        this.legacyLookup = legacyLookup;
    }

    @Override public void onAvailable(Network network) {
        main.execute(() -> {
            if (closed) return;
            current = network;
            // Android 8+ follows this event with a capabilities callback.
            if (legacyLookup != null) update.accept(legacyLookup.apply(network));
        });
    }

    @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
        main.execute(() -> {
            if (!closed && network.equals(current)) update.accept(caps);
        });
    }

    @Override public void onLost(Network network) {
        main.execute(() -> {
            if (!closed && network.equals(current)) {
                current = null;
                update.accept(null);
            }
        });
    }

    void close() { closed = true; }
}
