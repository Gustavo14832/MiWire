package com.limelight.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.limelight.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "pt-rBR-w960dp-h540dp-land-television-xhdpi")
public class MiWireDashboardTest {
    private Activity screen(int layout) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setContentView(layout);
        View content = activity.findViewById(android.R.id.content);
        content.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        content.layout(0, 0, 1920, 1080);
        return activity;
    }

    @Test public void remoteMovesAcrossTheFiveLabelledActions() {
        Activity activity = screen(R.layout.activity_pc_view);
        int[] actions = {R.id.settingsButton, R.id.overridesToggleButton, R.id.autoResumeToggleButton,
                R.id.helpButton, R.id.manuallyAddPc};
        for (int i = 0; i < actions.length; i++) {
            ImageButton button = activity.findViewById(actions[i]);
            assertTrue(button.isFocusable());
            assertTrue(button.getContentDescription().length() > 0);
            if (i + 1 < actions.length) {
                assertSame(activity.findViewById(actions[i + 1]), button.focusSearch(View.FOCUS_RIGHT));
            }
        }
        ViewGroup root = ((FrameLayout) activity.findViewById(android.R.id.content));
        assertEquals(Color.BLACK, ((ColorDrawable)root.getChildAt(0).getBackground()).getColor());
    }

    @Test public void wideLibraryPlacesTheCoverBesideTheActionsAndKeepsTheCanvasBlack() {
        Activity activity = screen(R.layout.activity_app_view);
        View hero = activity.findViewById(R.id.heroSection);
        View cover = hero.findViewById(R.id.heroCover);
        View actions = hero.findViewById(R.id.heroDetails);
        assertTrue(cover.getMeasuredWidth() > 0);
        assertTrue(actions.getRight() <= cover.getLeft());
        View root = activity.findViewById(R.id.appRoot);
        assertEquals(Color.BLACK, ((ColorDrawable)root.getBackground()).getColor());
    }

    @Test public void tvSettingsPlaceTheSectionRailBesideThePreferences() {
        Activity activity = screen(R.layout.activity_stream_settings);
        View rail = activity.findViewById(R.id.settingsSectionsScroll);
        assertTrue(rail instanceof android.widget.ScrollView);
        int[] railPosition = new int[2];
        int[] listPosition = new int[2];
        rail.getLocationInWindow(railPosition);
        activity.findViewById(R.id.stream_settings).getLocationInWindow(listPosition);
        assertTrue(railPosition[0] + rail.getWidth() <= listPosition[0]);
    }
}
