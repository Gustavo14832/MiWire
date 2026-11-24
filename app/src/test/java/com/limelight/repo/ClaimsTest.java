package com.limelight.repo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.limelight.RepoFiles;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks in-repo claims against tested behavior and documentation invariants.
 */
public class ClaimsTest {
    private static final String[][] UNBACKED = {
            {"(?i)turnip[^.\\n]*(faster|(?<!not listed with the )speed)", "the custom Turnip driver hasn't been measured"},
            {"(?i)(faster|speeds? up)[^.\\n]*turnip", "the custom Turnip driver hasn't been measured"},
            {"(?i)speed up vulkan", "the custom Turnip driver hasn't been measured"},
            {"(?i)faster video", "the video options haven't been measured in MiWire/Starwire"},
            {"(?i)\\bfast video\\b", "nothing has measured the video pipeline in MiWire/Starwire"},
            {"(?i)every millisecond counts", "the video options haven't been measured in MiWire/Starwire"},
            {"(?i)every feature is tested", "not every feature has been tried on real hardware"},
            {"(?i)fit every android device", "only narrow screens and foldable sizes are tested"},
            {"(?i)devices of every size", "only narrow screens and foldable sizes are tested"},
            {"(?i)(apollo|sunshine).*\\bworks too\\b", "only Vibepollo has been tried as a host"},
            {"(?i)rebuilds libadrenotools from source", "the libraries are prebuilt"},
            {"(?i)if the driver doesn.{1,2}t work, the system one is used", "a driver that crashes closes the app"},
            {"(?i)local network only", "the 0.2.0 microphone isn't protected by being on the local network"},
            {"(?i)(closed|destroyed)[^.\\n]*\\bcould crash", "no test has shown a late dialog crashing the app"},
    };

    private static List<String> textFiles() {
        List<String> files = new ArrayList<>(Arrays.asList("README.md", "CHANGELOG.md", "docs/ROADMAP.md",
                "app/src/main/res/values/strings.xml"));
        File[] notes = RepoFiles.file(".github/release-notes").listFiles((d, name) -> name.endsWith(".md"));
        assertNotNull(notes);
        for (File note : notes) {
            files.add(".github/release-notes/" + note.getName());
        }
        return files;
    }

    private static String string(String strings, String name) {
        Matcher m = Pattern.compile("<string name=\"" + name + "\">(.*?)</string>", Pattern.DOTALL).matcher(strings);
        assertTrue(name + " not in strings.xml", m.find());
        return m.group(1);
    }

    private static List<String> claimsIn(String line) {
        List<String> reasons = new ArrayList<>();
        for (String[] claim : UNBACKED) {
            if (Pattern.compile(claim[0]).matcher(line).find()) {
                reasons.add(claim[1]);
            }
        }
        return reasons;
    }

    @Test
    public void noClaimsThatNothingBacks() throws Exception {
        assertEquals(0, claimsIn("- **Clearer about what's experimental:** the custom Turnip driver is now"
                + " described as an experimental option, not listed with the speed features.").size());
        assertEquals(1, claimsIn("The custom Turnip driver is listed with the speed features.").size());
        assertEquals(1, claimsIn("A custom Turnip driver for more speed.").size());
        assertEquals(1, claimsIn("Load a Turnip driver for faster PyroWave.").size());
        assertEquals(1, claimsIn("  already closed, which could crash the app.").size());

        List<String> found = new ArrayList<>();
        for (String path : textFiles()) {
            String[] lines = RepoFiles.read(path).split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                for (String reason : claimsIn(lines[i])) {
                    found.add(path + ":" + (i + 1) + " (" + reason + "): " + lines[i].trim());
                }
            }
        }
        assertTrue("claims with nothing behind them:\n" + String.join("\n", found), found.isEmpty());
    }

    @Test
    public void theCustomDriverIsDescribedAsUntried() throws Exception {
        String untried = "not yet tried with a real turnip driver";
        assertTrue(RepoFiles.read("docs/ROADMAP.md").toLowerCase(Locale.ROOT).contains(untried));

        String security = RepoFiles.read("SECURITY.md").toLowerCase(Locale.ROOT);
        assertTrue(security.contains("experimental"));

        String strings = RepoFiles.read("app/src/main/res/values/strings.xml");
        assertTrue(string(strings, "title_custom_vulkan_driver").toLowerCase(Locale.ROOT).contains("experimental"));
        assertTrue(string(strings, "summary_custom_vulkan_driver").toLowerCase(Locale.ROOT).contains(untried));
        assertTrue(string(strings, "impact_custom_vulkan_driver").toLowerCase(Locale.ROOT).contains(untried));

        String warning = string(strings, "custom_vulkan_driver_warning_message");
        assertTrue(warning, warning.contains("Only use a driver file from a source you trust"));
        assertTrue(warning, warning.contains("goes back to the normal driver"));
        assertTrue(warning, warning.contains("A driver that crashes closes Starwire")
                || warning.contains("A driver that crashes closes MiWire"));
        assertTrue(warning, warning.contains("hasn\\'t been tried with a real Turnip driver"));
    }

    @Test
    public void theDesktopShortcutsAreDescribedAsUntried() throws Exception {
        String untried = "checked by tests, not yet tried in a stream";
        String changelog = RepoFiles.read("CHANGELOG.md").replace("\r", "");
        int entry = changelog.indexOf("**Shortcuts for Windows' virtual desktops in the stream menu:**");
        assertTrue("the changelog's desktop shortcuts entry", entry >= 0);
        int end = changelog.indexOf("\n\n", entry);
        String item = flat(changelog.substring(entry, end < 0 ? changelog.length() : end));
        assertTrue(item, item.toLowerCase(Locale.ROOT).contains(untried));
    }

    private static final String[] OLD_MIC_WARNINGS = {
            "In 0.2.0 the microphone audio isn't encrypted",
            "Until the fix ships: - Use it only on a network you trust",
            "Until the fix ships: - Use the microphone only on a network you trust",
            "I'm working on a fix. [SECURITY.md](SECURITY.md) has the details",
            "I'm working on a fix, [coming in 0.2.1]",
            "(`SECURITY.md` has the details); a fix is being worked on",
            "it isn't secure yet",
            "Not encrypted or authenticated in 0.2.0, so only for networks you trust",
            "Local network only, but not secure",
            "Known issue: the 0.2.0 microphone",
            "A VBAN receiver on the PC (any host)",
            "TODO (encrypted mic)",
    };

    private static String flat(String text) {
        return text.replaceAll("\\s+", " ");
    }

    @Test
    public void theDocsDescribeTheEncryptedMicrophone() throws Exception {
        String readme = flat(RepoFiles.read("README.md"));
        assertTrue("the README should mention encrypted SWMC v1 microphone",
                readme.contains("SWMC v1") && readme.toLowerCase(Locale.ROOT).contains("criptografad"));

        String guide = flat(RepoFiles.read("windows/README.md"));
        assertTrue("the guide's Please read first doesn't say the mic is encrypted and paired",
                guide.contains("From 0.2.1 the audio is encrypted on the way, and the receiver only plays it"
                + " from devices you've paired with that PC."));
        List<String> sections = new ArrayList<>();
        Matcher heading = Pattern.compile("(?m)^## (.*)$").matcher(RepoFiles.read("windows/README.md"));
        while (heading.find()) {
            sections.add(heading.group(1).trim());
        }
        int details = sections.indexOf("Details and fixes");
        int pairing = sections.indexOf("Pair Starwire with the PC (once)");
        int updating = sections.indexOf("Updating from Starwire 0.2.0");
        assertTrue(sections.toString(), details >= 0 && pairing >= 0 && updating >= 0);
        assertTrue(sections.toString(), pairing < details && updating < details);

        for (String path : new String[] {"README.md", "windows/README.md", "SECURITY.md", "docs/ROADMAP.md"}) {
            String text = flat(RepoFiles.read(path));
            for (String old : OLD_MIC_WARNINGS) {
                assertFalse(path + " still says: " + old, text.contains(old));
            }
            assertFalse(path + " still has the encrypted mic as coming",
                    Pattern.compile("(?i)coming in 0\\.2\\.1: (the microphone )?encrypted").matcher(text).find());
        }

        for (String label : new String[] {"Pair with the PC", "Pair a device...", "Show the code", "Not encrypted"}) {
            assertTrue(label, guide.contains(label));
        }
        String strings = RepoFiles.read("app/src/main/res/values/strings.xml");
        assertEquals("Pair with the PC", string(strings, "title_mic_pairing"));
        assertEquals("Send my microphone", string(strings, "title_checkbox_enable_microphone"));
        assertTrue("the app's VBAN option isn't labelled Not encrypted",
                string(strings, "microphone_target_vban").startsWith("Not encrypted"));
        String receiver = RepoFiles.read("windows/StarwireMicReceiver.cs");
        assertTrue("the receiver's menu has no Pair a device...",
                receiver.contains("menu.Items.Add(\"Pair a device...\""));
        assertTrue("the receiver's pairing window has no Show the code",
                receiver.contains("showCode.Text = \"Show the code\";"));

        assertTrue("the 0.2.0 release notes no longer say the audio isn't encrypted",
                RepoFiles.read(".github/release-notes/v0.2.0-notes.md").contains("The audio isn't encrypted"));
    }

    @Test
    public void theUnencryptedRisksAreKeptApart() throws Exception {
        String security = flat(RepoFiles.read("SECURITY.md"));
        assertTrue("SECURITY.md doesn't say Not encrypted can be listened to",
                security.contains("sends plain VBAN, so anyone on the same network can listen to the microphone."));
        assertTrue("SECURITY.md ties sound sent into the PC to the device's option",
                security.contains("whichever option your device uses"));
        String setup = RepoFiles.read("windows/setup-microphone.ps1");
        assertTrue("the setup's firewall rule no longer applies on every network type", setup.contains("-Profile Any"));
        assertTrue("SECURITY.md doesn't say the firewall rule applies on public Wi-Fi",
                security.contains("public Wi-Fi"));

        assertTrue("the setup has no -AllowUnencrypted", setup.contains("[switch]$AllowUnencrypted"));
        assertTrue("the receiver has no --allow-unencrypted",
                RepoFiles.read("windows/StarwireMicReceiver.cs").contains("--allow-unencrypted"));
        assertTrue("SECURITY.md doesn't say when the receiver plays plain VBAN",
                security.contains("only plays plain VBAN if set up with `-AllowUnencrypted`"));
    }

    @Test
    public void theEncryptedUploadFigureIsWorkedOut() throws Exception {
        assertTrue("mic-protocol.md no longer gives the 584-byte packet",
                RepoFiles.read("docs/dev/mic-protocol.md").contains("584 for Starwire's 540-byte packets"));
        double mbps = (584 + 8 + 20) * 187.5 * 8 / 1e6;
        String guide = flat(RepoFiles.read("windows/README.md"));
        String figure = String.format(Locale.ROOT, "**About %.2f Mbps**", mbps);
        assertTrue("the guide doesn't give " + figure, guide.contains(figure));
        assertTrue("the guide doesn't say the figure isn't measured", guide.contains("That's worked out, not measured yet."));
    }
}
