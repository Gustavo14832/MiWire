package com.limelight.preferences;

import static org.junit.Assert.*;

import android.content.Intent;
import android.content.ServiceConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ManualHostTest {
    @Test public void allPrefixesUseTheMostSignificantBits() {
        byte[] local = {(byte) 192, 0, 2, 33};
        for (int prefix = 0; prefix <= 32; prefix++) {
            for (int bit = 0; bit < 32; bit++) {
                byte[] peer = local.clone();
                peer[bit / 8] ^= (byte) (0x80 >>> (bit % 8));
                assertEquals("prefix=" + prefix + " changed bit=" + bit, bit >= prefix,
                        AddComputerManually.sameIpv4Subnet(peer, local, prefix));
            }
        }
        assertFalse(AddComputerManually.sameIpv4Subnet(local, local, -1));
        assertFalse(AddComputerManually.sameIpv4Subnet(local, local, 33));
    }

    @Test public void manualEntryAcceptsHostsAndPortsWithoutSilentlyDiscardingExtraParts() {
        for (String address : new String[] {"pc.example", "pc.example:65535", "::1", "[::1]:47989"}) {
            assertNotNull(address, AddComputerManually.parseRawUserInputToUri(address));
        }
        for (String address : new String[] {"pc.example:0", "pc.example:65536", "pc.example:",
                "pc.example/path", "pc.example?x", "pc.example#fragment", "name@pc.example"}) {
            assertNull(address, AddComputerManually.parseRawUserInputToUri(address));
        }
    }

    public static class PendingBindScreen extends AddComputerManually {
        ServiceConnection registered;
        boolean unbound;
        @Override public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
            registered = connection;
            return true;
        }
        @Override public void unbindService(ServiceConnection connection) {
            assertSame(registered, connection);
            unbound = true;
        }
    }

    @Test public void leavingBeforeTheServiceIsReadyReleasesItsBinding() {
        var controller = Robolectric.buildActivity(PendingBindScreen.class).create();
        PendingBindScreen screen = controller.get();
        assertNotNull(screen.registered);
        controller.destroy();
        assertTrue(screen.unbound);
    }
}

