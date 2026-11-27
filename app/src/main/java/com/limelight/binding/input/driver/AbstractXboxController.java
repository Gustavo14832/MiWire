package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractXboxController extends AbstractController {
    protected final UsbDevice device;
    protected final UsbDeviceConnection connection;

    private Thread inputThread;
    private final AtomicBoolean stopped = new AtomicBoolean();

    protected UsbEndpoint inEndpt, outEndpt;

    public AbstractXboxController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_XBOX;
        this.capabilities = MoonBridge.LI_CCAP_ANALOG_TRIGGERS | MoonBridge.LI_CCAP_RUMBLE;
        this.buttonFlags =
                ControllerPacket.A_FLAG | ControllerPacket.B_FLAG | ControllerPacket.X_FLAG | ControllerPacket.Y_FLAG |
                        ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG | ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG |
                        ControllerPacket.LB_FLAG | ControllerPacket.RB_FLAG |
                        ControllerPacket.LS_CLK_FLAG | ControllerPacket.RS_CLK_FLAG |
                        ControllerPacket.BACK_FLAG | ControllerPacket.PLAY_FLAG | ControllerPacket.SPECIAL_BUTTON_FLAG;
    }

    private Thread createInputThread() {
        return new Thread() {
            public void run() {
                try {
                    // Delay for a moment before reporting the new gamepad and
                    // accepting new input. This allows time for the old InputDevice
                    // to go away before we reclaim its spot. If the old device is still
                    // around when we call notifyDeviceAdded(), we won't be able to claim
                    // the controller number used by the original InputDevice.
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }

                // Report that we're added _before_ reporting input
                if (stopped.get()) return;
                notifyDeviceAdded();

                readInput();
            }
        };
    }

    void readInput() {
        byte[] buffer = new byte[64];
        ByteBuffer packet = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        while (!Thread.currentThread().isInterrupted() && !stopped.get()) {

            int res;

            //
            // There's no way that I can tell to determine if a device has failed
            // or if the timeout has simply expired. We'll check how long the transfer
            // took to fail and assume the device failed if it happened before the timeout
            // expired.
            //

            do {
                // Read the next input state packet
                long lastMillis = SystemClock.uptimeMillis();
                res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 3000);

                // If we get a zero length response, treat it as an error
                if (res == 0) {
                    res = -1;
                }

                if (res < 0 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                    LimeLog.warning("Detected device I/O error");
                    stop();
                    break;
                }
            } while (res < 0 && !Thread.currentThread().isInterrupted() && !stopped.get());

            if (res < 0 || stopped.get()) {
                break;
            }

            packet.clear();
            packet.limit(res);
            if (handleRead(packet)) {
                // Report input if handleRead() returns true
                reportInput();
            }
        }
    }

    public boolean start() {
        // The supported wired protocols use interface zero for commands and input.
        // Other interfaces on a composite controller may belong to Android.
        UsbInterface iface = device.getInterface(0);
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                if (inEndpt != null) {
                    LimeLog.warning("Found duplicate IN endpoint");
                    return false;
                }
                inEndpt = endpt;
            }
            else if (endpt.getDirection() == UsbConstants.USB_DIR_OUT) {
                if (outEndpt != null) {
                    LimeLog.warning("Found duplicate OUT endpoint");
                    return false;
                }
                outEndpt = endpt;
            }
        }

        // Make sure the required endpoints were present
        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("Missing required endpoint");
            return false;
        }

        if (!connection.claimInterface(iface, true)) {
            LimeLog.warning("Failed to claim controller interface");
            return false;
        }
        boolean running = false;
        try {
            if (!doInit()) return false;
            inputThread = createInputThread();
            inputThread.start();
            running = true;
            return true;
        } finally {
            if (!running) connection.releaseInterface(iface);
        }
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }

        // Cancel any rumble effects
        rumble((short)0, (short)0);

        // Stop the input thread
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }

        // Android's release operation lets the kernel reattach its original driver.
        connection.releaseInterface(device.getInterface(0));
        connection.close();

        // Report the device removed
        notifyDeviceRemoved();
    }

    protected abstract boolean handleRead(ByteBuffer buffer);
    protected abstract boolean doInit();
}
