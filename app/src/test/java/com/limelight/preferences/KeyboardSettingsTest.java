package com.limelight.preferences;

import static org.junit.Assert.assertTrue;

import com.limelight.RepoFiles;

import org.junit.Test;

import java.util.regex.Pattern;

/** Force QWERTY layout and Back button as Meta: the settings page entries, their defaults and their text. */
public class KeyboardSettingsTest {
    private static boolean hasDefault(String prefsXml, String key, boolean value) {
        return Pattern.compile("android:key=\"" + key + "\"[^>]*android:defaultValue=\"" + value + "\"")
                .matcher(prefsXml).find();
    }

    // Starwire already sent a keyboard's keys as QWERTY, so turning the setting on must change nothing
    @Test
    public void forceQwertyIsOnByDefault() throws Exception {
        String prefs = RepoFiles.read("app/src/main/res/xml/preferences.xml");
        assertTrue(hasDefault(prefs, "checkbox_force_qwerty", true));
    }

    @Test
    public void backAsMetaIsOffByDefault() throws Exception {
        String prefs = RepoFiles.read("app/src/main/res/xml/preferences.xml");
        assertTrue(hasDefault(prefs, "checkbox_back_as_meta", false));
    }

    @Test
    public void bothSettingsHaveTheirText() throws Exception {
        String strings = RepoFiles.read("app/src/main/res/values/strings.xml");
        for (String name : new String[] {"title_checkbox_force_qwerty", "summary_checkbox_force_qwerty",
                "title_checkbox_back_as_meta", "summary_checkbox_back_as_meta"}) {
            assertTrue(name, strings.contains("<string name=\"" + name + "\">"));
        }
    }
}
