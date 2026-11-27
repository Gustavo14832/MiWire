package com.limelight.binding.input.capture;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class HubPointerCaptureTest {
    private boolean focus = true, captured, compatible = true;
    private int scans, requests, releases;
    private AndroidNativePointerCaptureProvider provider;
    @Before public void setup() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        View view = new View(activity) {
            @Override public boolean hasWindowFocus() { return focus; }
            @Override public boolean hasPointerCapture() { return captured; }
            @Override public void requestPointerCapture() { requests++; captured = true; }
            @Override public void releasePointerCapture() { releases++; captured = false; }
        };
        provider = new AndroidNativePointerCaptureProvider(activity, view) {
            @Override boolean hasCaptureCompatibleInputDevice() { scans++; return compatible; }
        };
        provider.enableCapture();
        assertTrue(captured);
        requests = releases = scans = 0;
    }
    @Test public void aHubBurstChecksOnceAndReleasesOnlyAfterTheLastMouse() {
        for (int i = 0; i < 24; i++) provider.onInputDeviceChanged(i);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, scans);
        assertEquals(0, releases);
        compatible = false;
        provider.onInputDeviceRemoved(4);
        provider.onInputDeviceRemoved(3);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, releases);
        compatible = true;
        provider.onInputDeviceAdded(4);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, requests);
    }
    @Test public void staleCallbacksCannotCaptureAfterHideOrFocusLoss() {
        provider.onInputDeviceAdded(1);
        provider.showCursor();
        provider.onInputDeviceChanged(1);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, scans);
        focus = false;
        provider.hideCursor();
        provider.onInputDeviceChanged(1);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, requests);
    }
    @Test @Config(sdk = 29) public void androidTenSettlingIsPreservedAndCancelledOnDestroy() {
        captured = false;
        provider.onWindowFocusChanged(true);
        provider.onInputDeviceAdded(1);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(499));
        assertEquals(0, requests);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(1, requests);
        provider.onWindowFocusChanged(true);
        provider.destroy();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(1, requests);
    }
}
