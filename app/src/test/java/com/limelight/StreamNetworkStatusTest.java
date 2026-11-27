package com.limelight;

import android.content.Context;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetworkCapabilities;
import org.robolectric.shadows.ShadowNetwork;
import org.robolectric.shadows.ShadowWifiInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "en")
public class StreamNetworkStatusTest {
    private final Context context = RuntimeEnvironment.getApplication();

    private NetworkCapabilities route(int... transports) {
        NetworkCapabilities caps = ShadowNetworkCapabilities.newInstance();
        for (int transport : transports) shadowOf(caps).addTransportType(transport);
        return caps;
    }

    @Test public void ethernetAndDisconnectionReleaseTheRadio() {
        NetworkCapabilities wired = route(NetworkCapabilities.TRANSPORT_ETHERNET);
        assertFalse(StreamNetworkStatus.wantsWifiLock(wired));
        assertFalse(StreamNetworkStatus.wantsWifiLock(null));
        assertFalse(StreamNetworkStatus.wantsWifiLock(route(NetworkCapabilities.TRANSPORT_CELLULAR)));
        assertEquals("Ethernet", StreamNetworkStatus.describe(context, wired));
        assertEquals("USB", StreamNetworkStatus.describe(context, route(NetworkCapabilities.TRANSPORT_USB)));
        assertEquals("unavailable", StreamNetworkStatus.describe(context, null));
    }

    @Test public void wifiWorksWithoutRadioDetails() {
        NetworkCapabilities wifi = route(NetworkCapabilities.TRANSPORT_WIFI);
        assertTrue(StreamNetworkStatus.wantsWifiLock(wifi));
        assertEquals("Wi-Fi", StreamNetworkStatus.describe(context, wifi));
        NetworkCapabilities both = route(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET);
        assertTrue(StreamNetworkStatus.wantsWifiLock(both));
        assertEquals("Ethernet + Wi-Fi", StreamNetworkStatus.describe(context, both));
    }

    @Test public void wifiFrequencyIsShownOnlyWhenReported() {
        NetworkCapabilities wifi = route(NetworkCapabilities.TRANSPORT_WIFI);
        WifiInfo radio = ShadowWifiInfo.newInstance();
        shadowOf(wifi).setTransportInfo(radio);
        shadowOf(radio).setFrequency(-1);
        assertEquals("Wi-Fi", StreamNetworkStatus.describe(context, wifi));
        shadowOf(radio).setFrequency(5180);
        assertEquals("Wi-Fi · 5180 MHz", StreamNetworkStatus.describe(context, wifi));
    }

    @Test public void handoverIgnoresOldNetworkAndQueuedCallbacksAfterClose() {
        Network a = ShadowNetwork.newInstance(10), b = ShadowNetwork.newInstance(11);
        NetworkCapabilities wifi = route(NetworkCapabilities.TRANSPORT_WIFI);
        NetworkCapabilities wired = route(NetworkCapabilities.TRANSPORT_ETHERNET);
        ArrayDeque<Runnable> main = new ArrayDeque<>();
        List<NetworkCapabilities> seen = new ArrayList<>();
        StreamNetworkCallback callback = new StreamNetworkCallback(a, main::add, seen::add, null);
        callback.onCapabilitiesChanged(a, wifi);
        callback.onAvailable(b);
        callback.onCapabilitiesChanged(b, wired);
        callback.onLost(a);
        callback.onCapabilitiesChanged(a, wifi);
        callback.onLost(b);
        while (!main.isEmpty()) main.remove().run();
        assertEquals(java.util.Arrays.asList(wifi, wired, null), seen);
        callback.onAvailable(a);
        callback.onCapabilitiesChanged(a, wifi);
        callback.close();
        while (!main.isEmpty()) main.remove().run();
        assertEquals(3, seen.size());
    }

    @Test public void olderAndroidCanUseTheAvailabilitySnapshot() {
        NetworkCapabilities wifi = route(NetworkCapabilities.TRANSPORT_WIFI);
        List<NetworkCapabilities> seen = new ArrayList<>();
        StreamNetworkCallback callback = new StreamNetworkCallback(null, Runnable::run, seen::add, network -> wifi);
        callback.onAvailable(ShadowNetwork.newInstance(10));
        assertEquals(java.util.Collections.singletonList(wifi), seen);
    }

    @Test public void vpnUsesItsReportedUnderlyingTransport() {
        int vpn = NetworkCapabilities.TRANSPORT_VPN;
        assertFalse(StreamNetworkStatus.wantsWifiLock(route(vpn, NetworkCapabilities.TRANSPORT_ETHERNET)));
        assertFalse(StreamNetworkStatus.wantsWifiLock(route(vpn, NetworkCapabilities.TRANSPORT_CELLULAR)));
        assertFalse(StreamNetworkStatus.wantsWifiLock(route(vpn, NetworkCapabilities.TRANSPORT_USB)));
        assertTrue(StreamNetworkStatus.wantsWifiLock(route(vpn, NetworkCapabilities.TRANSPORT_WIFI)));
        assertTrue(StreamNetworkStatus.wantsWifiLock(route(vpn)));
        assertEquals("VPN · Ethernet", StreamNetworkStatus.describe(context, route(vpn, NetworkCapabilities.TRANSPORT_ETHERNET)));
        assertEquals("VPN", StreamNetworkStatus.describe(context, route(vpn)));
    }

    @Test public void absentRttIsExplicitInBothOverlays() {
        assertEquals("Network RTT unavailable", StreamNetworkStatus.formatRtt(context, -1L, false, 6.5f));
        assertEquals("Ping — · Decode 6.5 ms", StreamNetworkStatus.formatRtt(context, -1L, true, 6.5f));
    }

    @Test public void measuredRttAndVariationUseSeparateWords() {
        long packed = (27L << 32) | 4;
        assertEquals("Network latency: 27 ms (variance 4 ms)", StreamNetworkStatus.formatRtt(context, packed, false, 6.5f));
        assertEquals("Ping 27 ms · Decode 6.5 ms", StreamNetworkStatus.formatRtt(context, packed, true, 6.5f));
    }
}
