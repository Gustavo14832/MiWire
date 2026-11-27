package com.limelight.binding.input.driver;

import android.hardware.usb.*;
import android.os.Parcelable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.shadow.api.Shadow;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {HubXboxInterfaceTest.Device.class, HubXboxInterfaceTest.Connection.class})
public class HubXboxInterfaceTest {
    @Implements(UsbDevice.class)
    public static class Device {
        static UsbInterface[] interfaces;
        @Implementation protected int getInterfaceCount() {return interfaces.length;}
        @Implementation protected UsbInterface getInterface(int n) {return interfaces[n];}
    }
    @Implements(UsbDeviceConnection.class)
    public static class Connection {
        static final List<Integer> claims = new ArrayList<>(), releases = new ArrayList<>();
        static int reads;
        static byte[] first;
        @Implementation protected boolean claimInterface(UsbInterface i, boolean force) {
            claims.add(i.getId());
            return i.getId() == 0;
        }
        @Implementation protected boolean releaseInterface(UsbInterface i) {releases.add(i.getId()); return true;}
        @Implementation protected void close() {}
        @Implementation protected int bulkTransfer(UsbEndpoint ep, byte[] buf, int len, int timeout) {
            if (reads++ == 0) first = buf;
            else assertSame(first, buf);
            buf[0] = (byte)reads;
            return reads % 2 == 0 ? 6 : 18;
        }
    }
    private static UsbInterface iface(int id, int... addresses) throws Exception {
        UsbInterface iface = UsbInterface.class.getConstructor(int.class, int.class, String.class, int.class,
                int.class, int.class).newInstance(id, 0, null, id == 0 ? 255 : 1, 93, 1);
        Parcelable[] endpoints = new Parcelable[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            endpoints[i] = UsbEndpoint.class.getConstructor(int.class, int.class, int.class, int.class)
                    .newInstance(addresses[i], 3, 64, 4);
        }
        UsbInterface.class.getMethod("setEndpoints", Parcelable[].class).invoke(iface, (Object)endpoints);
        return iface;
    }
    private Pad pad;
    private static class Pad extends AbstractXboxController {
        int init;
        ByteBuffer first;
        Pad() {super(Shadow.newInstanceOf(UsbDevice.class), Shadow.newInstanceOf(UsbDeviceConnection.class), 1,
                new UsbDriverListener() {
                    public void deviceAdded(AbstractController c) {}
                    public void deviceRemoved(AbstractController c) {}
                    public void reportControllerState(int id,int flags,float lx,float ly,float rx,float ry,float lt,float rt) {}
                });}
        protected boolean doInit() {init++; return false;}
        protected boolean handleRead(ByteBuffer buf) {
            if (first == null) first = buf;
            assertSame(first, buf);
            assertEquals(ByteOrder.LITTLE_ENDIAN, buf.order());
            assertEquals(Connection.reads % 2 == 0 ? 6 : 18, buf.remaining());
            assertEquals((byte)Connection.reads, buf.get());
            if (Connection.reads == 1000) stop();
            return false;
        }
        public void rumble(short a, short b) {}
        public void rumbleTriggers(short a, short b) {}
    }
    @Before public void setUp() throws Exception {
        Device.interfaces = new UsbInterface[]{iface(0,129,2), iface(1,131)};
        Connection.claims.clear(); Connection.releases.clear();
        Connection.reads = 0; Connection.first = null;
        pad = new Pad();
    }
    @Test public void unrelatedOccupiedInterfaceIsNeverClaimedAndFailedInitReturnsTheGamepad() {
        assertFalse(pad.start());
        assertEquals(1, pad.init);
        assertEquals(java.util.Collections.singletonList(0), Connection.claims);
        assertEquals(Connection.claims, Connection.releases);
    }
    @Test public void incompleteEndpointPairLeavesTheKernelDriverAlone() throws Exception {
        Device.interfaces[0] = iface(0,129);
        assertFalse(pad.start());
        assertTrue(Connection.claims.isEmpty());
        assertEquals(0, pad.init);
    }
    @Test public void aThousandReportsUseOneBufferWithTheCorrectLength() {
        pad.readInput();
        assertEquals(1000, Connection.reads);
    }
}
