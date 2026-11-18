package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.preference.PreferenceManager;

import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.preferences.PreferenceConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.MediaCodecInfoBuilder;

/**
 * Verifies the Xiaomi Mi Box S Gen 3 (Amlogic S905X5M, Android 14 TV) optimizations:
 * TV out-of-the-box gaming defaults, Amlogic Codec2 vendor low-latency configuration,
 * sub-pixel mouse delta accumulation, trigger deadzone floor, and 60 Hz display mode switching.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, instrumentedPackages = {"com.limelight.nvstream.jni"})
public class MiBoxOptimizationTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
        MediaCodecHelper.initialize(context, "");
    }

    @Test
    public void tvDefaultsAreSeededOnceAndRespectUserChanges() {
        // Non-TV phones must remain untouched
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, false);
        assertFalse(prefs.contains("list_resolution"));
        assertFalse(prefs.contains("video_format"));

        // TV / Amlogic boxes get calibrated defaults
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertEquals("1920x1080", prefs.getString("list_resolution", ""));
        assertEquals("60", prefs.getString("list_fps", ""));
        assertEquals(35000, prefs.getInt("seekbar_bitrate_kbps", 0));
        assertEquals("forceh265", prefs.getString("video_format", ""));
        assertEquals("vulkan", prefs.getString("video_renderer", ""));
        assertEquals("low", prefs.getString("spatial_dithering", ""));
        assertEquals("mibox-60hz", prefs.getString("frame_pacing", ""));
        assertEquals("off", prefs.getString("vsync_mode", ""));
        assertFalse(prefs.getBoolean("checkbox_async_decode", true));
        assertFalse(prefs.getBoolean("checkbox_async_lfr", true));
        assertFalse(prefs.getBoolean("checkbox_usb_driver", true));
        assertFalse(prefs.getBoolean("checkbox_vibrate_fallback", true));
        assertEquals("lb_rb", prefs.getString("overlay_trigger_button", ""));
        assertFalse(prefs.getBoolean("checkbox_enable_floating_button", true));
        assertEquals(3, prefs.getInt("seekbar_deadzone", -1));
        assertFalse(prefs.getBoolean("checkbox_gamepad_motion_sensors", true));
        assertFalse(prefs.getBoolean("checkbox_enable_sops", true));

        // User edits persist on subsequent calls
        prefs.edit().putInt("seekbar_bitrate_kbps", 50000).putString("list_fps", "120").commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertEquals(50000, prefs.getInt("seekbar_bitrate_kbps", 0));
        assertEquals("120", prefs.getString("list_fps", ""));
    }

    @Test
    public void tvDefaultsV1UpgradesCleanlyToV2() {
        prefs.edit()
                .putBoolean("mibox_tv_gaming_defaults_v1", true)
                .putString("frame_pacing", "latency")
                .putString("vsync_mode", "fast")
                .putBoolean("checkbox_async_decode", true)
                .putBoolean("checkbox_async_lfr", true)
                .putInt("seekbar_bitrate_kbps", 45000)
                .putString("list_fps", "60")
                .commit();

        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);

        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v2", false));
        assertEquals("vulkan", prefs.getString("video_renderer", ""));
        assertEquals("low", prefs.getString("spatial_dithering", ""));
        assertEquals("mibox-60hz", prefs.getString("frame_pacing", ""));
        assertEquals("off", prefs.getString("vsync_mode", ""));
        assertFalse(prefs.getBoolean("checkbox_async_decode", true));
        assertFalse(prefs.getBoolean("checkbox_async_lfr", true));
        assertFalse(prefs.getBoolean("checkbox_usb_driver", true));
        assertFalse(prefs.getBoolean("checkbox_vibrate_fallback", true));
        assertEquals(45000, prefs.getInt("seekbar_bitrate_kbps", 0));
        assertEquals("60", prefs.getString("list_fps", ""));

        // Repeated calls do not clobber explicit user choices
        prefs.edit().putString("frame_pacing", "host-timed").putBoolean("checkbox_usb_driver", true).commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertEquals("host-timed", prefs.getString("frame_pacing", ""));
        assertTrue(prefs.getBoolean("checkbox_usb_driver", false));
    }

    @Test
    public void miboxFramePacingModeAndDirectSubmitAndAudioHelpers() {
        assertTrue(PreferenceConfiguration.isHostTimedPacing(PreferenceConfiguration.FRAME_PACING_MIBOX_60HZ));
        assertTrue(PreferenceConfiguration.isHostTimedPacing(PreferenceConfiguration.FRAME_PACING_HOST_TIMED));
        assertFalse(PreferenceConfiguration.isHostTimedPacing(PreferenceConfiguration.FRAME_PACING_BALANCED));
        assertEquals(1, PreferenceConfiguration.warpFactorFor("mibox-60hz"));

        assertTrue(MediaCodecHelper.decoderCanDirectSubmit("c2.amlogic.hevc.decoder"));
        assertTrue(MediaCodecHelper.decoderCanDirectSubmit("OMX.amlogic.hevc.decoder.awesome"));

        assertTrue(AndroidAudioRenderer.shouldAttemptLowLatencyAudio(34, 44100, 48000, false));
        assertFalse(AndroidAudioRenderer.shouldAttemptLowLatencyAudio(26, 44100, 48000, false));
        assertFalse(AndroidAudioRenderer.shouldAttemptLowLatencyAudio(34, 48000, 48000, true));
    }

    @Test
    public void amlogicCodec2UsesVendorLowLatencyWithoutAndroidLowLatencyKey() {
        CodecProfileLevel profileLevel = new CodecProfileLevel();
        profileLevel.profile = CodecProfileLevel.HEVCProfileMain;
        profileLevel.level = CodecProfileLevel.HEVCMainTierLevel51;
        CodecCapabilities caps = MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                .setMediaFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080))
                .setIsEncoder(false)
                .setProfileLevels(new CodecProfileLevel[] {profileLevel})
                .setColorFormats(new int[] {CodecCapabilities.COLOR_FormatYUV420Flexible})
                .setRequiredFeatures(new String[] {CodecCapabilities.FEATURE_LowLatency})
                .build();
        MediaCodecInfo amlogic = MediaCodecInfoBuilder.newBuilder()
                .setName("c2.amlogic.hevc.decoder")
                .setIsSoftwareOnly(false)
                .setIsHardwareAccelerated(true)
                .setCapabilities(caps)
                .build();

        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60);

        MediaCodecHelper.setDecoderLowLatencyOptions(format, amlogic, false, 0);

        assertFalse("generic low-latency key stalls c2.amlogic", format.containsKey("low-latency"));
        assertEquals(1, format.getInteger("vdec-lowlatency"));
        assertEquals(1, format.getInteger("vendor.low-latency.enable"));
        assertEquals(1, format.getInteger("vendor.amlogic.low-latency"));
        assertEquals(1, format.getInteger("vendor.amlogic.vdec.low-latency"));
        assertEquals(1, format.getInteger("vendor.vdec.low-latency"));
        assertEquals(1, format.getInteger("vendor.media.omx.gamemode"));
        assertEquals(1, format.getInteger("vendor.amlogic.vdec.boost"));
        assertEquals(0, format.getInteger(MediaFormat.KEY_PRIORITY));
        int operatingRate = format.getInteger(MediaFormat.KEY_OPERATING_RATE);
        assertTrue("unexpected operating rate: " + operatingRate, operatingRate >= 120 && operatingRate <= 240);

        assertTrue(MediaCodecHelper.shouldSkipPostStartLowLatency("c2.amlogic.hevc.decoder"));
        assertFalse(MediaCodecHelper.shouldSkipPostStartLowLatency("c2.qti.hevc.decoder"));
    }

    @Test
    public void subpixelMouseAccumulatorPreservesFractionalMovements() {
        Game.SubpixelMouseAccumulator acc = new Game.SubpixelMouseAccumulator();
        assertEquals(0, acc.consumeX(0.4f));
        assertEquals(0, acc.consumeX(0.4f));
        assertEquals(1, acc.consumeX(0.4f));
        // 0.2f remainder carried forward -> +0.8f reaches 1
        assertEquals(1, acc.consumeX(0.8f));

        // Negative deltas accumulate cleanly as well
        assertEquals(0, acc.consumeY(-0.4f));
        assertEquals(0, acc.consumeY(-0.4f));
        assertEquals(-1, acc.consumeY(-0.4f));

        acc.reset();
        assertEquals(0, acc.consumeX(0.4f));
        assertEquals(0, acc.consumeY(-0.4f));
    }

    @Test
    public void triggerDeadzoneUsesTwoPercentFloor() {
        assertEquals(0.02f, ControllerHandler.sanitizeTriggerDeadzone(0.0f), 1e-6f);
        assertEquals(0.02f, ControllerHandler.sanitizeTriggerDeadzone(0.01f), 1e-6f);
        assertEquals(0.05f, ControllerHandler.sanitizeTriggerDeadzone(0.05f), 1e-6f);
        assertEquals(0.02f, ControllerHandler.sanitizeTriggerDeadzone(0.50f), 1e-6f);
    }

    @Test
    public void androidTvAppliesPreferredDisplayModeAtMatchingResolution() {
        assertTrue(Game.shouldApplyPreferredDisplayMode(34, "aquaman", true, 1920, 1080, 1920, 1080));
        assertFalse(Game.shouldApplyPreferredDisplayMode(34, "sabrina", true, 1920, 1080, 1920, 1080));
        assertFalse(Game.shouldApplyPreferredDisplayMode(34, "pixel", false, 1920, 1080, 1920, 1080));
        assertTrue(Game.isCloserExactRefreshMatch(60.0f, 59.94f, 60));
        assertFalse(Game.isCloserExactRefreshMatch(59.94f, 60.0f, 60));
    }
}
