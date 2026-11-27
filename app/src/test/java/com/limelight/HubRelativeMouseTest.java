package com.limelight;

import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import android.widget.TextView;
import com.limelight.binding.input.capture.AndroidNativePointerCaptureProvider;
import com.limelight.preferences.PreferenceConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowInputDevice;
import org.robolectric.shadows.ShadowInputEvent;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = HubRelativeMouseTest.DeviceLookup.class)
public class HubRelativeMouseTest {
    @Implements(InputDevice.class)
    public static class DeviceLookup extends ShadowInputDevice {
        static int calls;
        @Implementation protected int getSources() { calls++; return InputDevice.SOURCE_MOUSE; }
    }
    @Test public void aThousandRelativeMovesKeepTheirDeltasWithoutLookingUpTheDevice() {
        Game game = Robolectric.buildActivity(Game.class).get();
        View view = new View(game);
        int[] totals = new int[3];
        RecordingConnection conn = new RecordingConnection(game) {
            @Override public void sendMouseMove(short dx, short dy) {
                totals[0]++; totals[1] += dx; totals[2] += dy;
            }
        };
        ReflectionHelpers.setField(game, "grabbedInput", true);
        PreferenceConfiguration prefs = new PreferenceConfiguration();
        ReflectionHelpers.setField(game, "prefConfig", prefs);
        ReflectionHelpers.setField(game, "conn", conn);
        ReflectionHelpers.setField(game, "inputCaptureProvider", new AndroidNativePointerCaptureProvider(game, view) {
            @Override public boolean isCapturingActive() {return true;}
        });
        MotionEvent.PointerProperties p = new MotionEvent.PointerProperties();
        p.id = 0; p.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = 2; c.y = -1;
        MotionEvent event = MotionEvent.obtain(0, 1, MotionEvent.ACTION_MOVE, 1,
                new MotionEvent.PointerProperties[]{p}, new MotionEvent.PointerCoords[]{c},
                0, 0, 1, 1, 17, 0, InputDevice.SOURCE_MOUSE_RELATIVE, 0);
        try {
            InputDevice mouse = ShadowInputDevice.makeInputDeviceNamed("Mouse");
            ((ShadowInputEvent) Shadow.extract(event)).setDevice(mouse);
            assertSame(mouse, event.getDevice());
            DeviceLookup.calls = 0;
            // The classifier in b4370584 did this before checking the source.
            for (int i = 0; i < 1000; i++) {
                int sources = event.getDevice() != null ? event.getDevice().getSources() : 0;
                assertEquals(InputDevice.SOURCE_MOUSE, sources);
            }
            assertEquals(1000, DeviceLookup.calls);
            DeviceLookup.calls = 0;
            for (int i = 0; i < 1000; i++) assertTrue(game.onGenericMotion(view, event));
            assertArrayEquals(new int[]{1000,2000,-1000}, totals);
            assertEquals(0, DeviceLookup.calls);
            MouseDispatchStats stats = ReflectionHelpers.getField(game, "mouseDispatchStats");
            assertEquals(0, stats.batchCount());
            prefs.enablePerfOverlay = true;
            prefs.enablePerfOverlayLite = true;
            assertTrue(game.onGenericMotion(view, event));
            assertEquals(0, stats.batchCount());
            prefs.enablePerfOverlayLite = false;
            long now = SystemClock.uptimeMillis();
            event.addBatch(now, new MotionEvent.PointerCoords[]{c}, 0);
            assertTrue(game.onGenericMotion(view, event));
            assertEquals(1, stats.batchCount());
            TextView overlay = new TextView(game);
            ReflectionHelpers.setField(game, "performanceOverlayView", overlay);
            game.onPerfUpdate("Stream");
            assertTrue(overlay.getText().toString().contains("p95"));
            assertEquals(0, stats.batchCount());
        } finally {
            event.recycle();
        }
    }
}
