package com.limelight.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.limelight.RepoFiles;

import org.junit.Test;

/**
 * The credit line a setting shows under the Predicted impact panel, for what came from another
 * Moonlight version. CoverGlowTest checks more of them on a device.
 */
public class SettingCreditsTest {
    @Test
    public void theLeftEdgeSwipeSettingCreditsMoonlightX() {
        assertEquals("From Moonlight X by MoreOrLessSoftware", CoverGlowSettings.creditFor("left_edge_swipe_action"));
    }

    @Test
    public void theTrackpadSettingIsMoonlightsOwnAgain() {
        assertNull(CoverGlowSettings.creditFor("checkbox_touchscreen_trackpad"));
    }

    @Test
    public void theDualSenseSettingCreditsVPlusAndNightjar() throws Exception {
        namesTheSamePeople("checkbox_dualsense_effects",
                "Moonlight V+", "qiin2333", "Nightjar", "Ramazan Kara");
    }

    @Test
    public void theBackOpensTheMenuSettingCreditsArtemisAndArtemide() throws Exception {
        namesTheSamePeople("checkbox_back_opens_menu",
                "Artemis", "Yukino Song", "Artemide");
    }

    private static void namesTheSamePeople(String key, String... names) throws Exception {
        assertTrue("the credit is for a setting that isn't there",
                RepoFiles.read("app/src/main/res/xml/preferences.xml").contains("android:key=\"" + key + "\""));
        String credit = CoverGlowSettings.creditFor(key);
        assertNotNull(key + " has no credit line", credit);
        String readme = RepoFiles.read("README.md");
        int start = readme.indexOf("### Créditos ao Projeto Original");
        assertTrue("README should preserve the upstream Credits section", start >= 0);
        String credits = readme.substring(start);
        for (String name : names) {
            assertTrue(key + "'s credit doesn't name " + name + ": " + credit, credit.contains(name));
            assertTrue("the README's Credits section doesn't name " + name, credits.contains(name));
        }
    }
}
