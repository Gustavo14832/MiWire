package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The full MiWire HUD keeps rounded corners and a slim amber edge.
 * With no game in its Intent, Game.onCreate sets up the overlays and then finishes before
 * anything connects. Artemide's Lite stats keep their own pill.
 */
@RunWith(RobolectricTestRunner.class)
// MoonBridge loads the native streaming library in its static block. Instrumenting it lets
// Robolectric skip loadLibrary and stub the native calls.
@Config(sdk = 36, instrumentedPackages = {"com.limelight.nvstream.jni"})
public class GameStatsBoxTest {
    @Test
    public void fullHudHasAnAmberEdgeAndRoundedBlackFace() {
        Context context = RuntimeEnvironment.getApplication();
        Game game = Robolectric.buildActivity(Game.class, new Intent(context, Game.class)).create().get();
        View stats = game.findViewById(R.id.performanceOverlay);

        Drawable background = stats.getBackground();
        assertTrue(background instanceof LayerDrawable);
        LayerDrawable layers = (LayerDrawable) background;
        assertEquals(2, layers.getNumberOfLayers());
        GradientDrawable edge = (GradientDrawable) layers.getDrawable(0);
        GradientDrawable face = (GradientDrawable) layers.getDrawable(1);
        float density = context.getResources().getDisplayMetrics().density;
        assertEquals(4 * density, edge.getCornerRadius(), 0.01f);
        assertEquals(3 * density, face.getCornerRadius(), 0.01f);
        assertEquals(Math.round(2 * density), layers.getLayerInsetLeft(1));
        assertEquals(context.getColor(R.color.mw_amber), edge.getColor().getDefaultColor());
        assertEquals(android.graphics.Color.BLACK, face.getColor().getDefaultColor());
    }
}
