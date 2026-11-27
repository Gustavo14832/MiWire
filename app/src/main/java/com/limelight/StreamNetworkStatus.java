package com.limelight;

import android.content.Context;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.os.Build;

/** Describes Android's default network. An existing stream socket may still use an older route. */
public final class StreamNetworkStatus {
    private StreamNetworkStatus() {}

    static boolean wantsWifiLock(NetworkCapabilities caps) {
        if (caps == null) return false;
        // A VPN can omit its underlying transport. Preserve Wi-Fi operation in that case.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        !caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                                !caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)));
    }

    static String describe(Context context, NetworkCapabilities caps) {
        if (caps == null) return context.getString(R.string.network_unavailable);
        boolean vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        String link;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            link = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet + Wi-Fi" : "Wi-Fi";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && caps.getTransportInfo() instanceof WifiInfo) {
                WifiInfo info = (WifiInfo) caps.getTransportInfo();
                if (info.getFrequency() > 0) link += " · " + info.getFrequency() + " MHz";
            }
        } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            link = "Ethernet";
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) {
            link = "USB";
        } else {
            return vpn ? "VPN" : context.getString(R.string.network_other);
        }
        return vpn ? "VPN · " + link : link;
    }

    public static String formatRtt(Context context, long packedRtt, boolean compact, float decodeMs) {
        if (packedRtt == -1L) {
            return compact ? context.getString(R.string.perf_overlay_lite_no_rtt, decodeMs)
                    : context.getString(R.string.perf_overlay_no_rtt);
        }
        int rttMs = (int) (packedRtt >>> 32);
        return compact ? context.getString(R.string.perf_overlay_lite_delay, rttMs, decodeMs)
                : context.getString(R.string.perf_overlay_netlatency, rttMs, (int) packedRtt);
    }
}
