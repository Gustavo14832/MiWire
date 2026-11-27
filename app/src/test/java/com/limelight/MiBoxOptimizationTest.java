package com.limelight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioDeviceInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaFormat;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.audio.AudioHaptics;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.ControllerStateSender;
import com.limelight.binding.input.StickCenterStore;
import com.limelight.binding.video.AdpfHelper;
import com.limelight.binding.video.DecoderInputBufferSizing;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PacingStats;
import com.limelight.grid.AppGridAdapter;
import com.limelight.grid.assets.CachedAppAssetLoader;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.grid.assets.MemoryAssetLoader;
import com.limelight.grid.assets.NetworkAssetLoader;
import com.limelight.grid.assets.ScaledBitmap;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.AppPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AppIcons;
import com.limelight.ui.CoverGlow;

import java.io.File;

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
        assertEquals("high", prefs.getString("spatial_dithering", ""));
        assertEquals("balanced", prefs.getString("vulkan_sharpening", ""));
        assertTrue(prefs.getBoolean("checkbox_full_range", false));
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
        assertFalse(prefs.getBoolean("checkbox_exact_slices", true));
        assertEquals(20, prefs.getInt("seekbar_max_pending_audio_ms", 0));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v5", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v6", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v7", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v8", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v9", false));
        assertFalse(prefs.getBoolean("checkbox_sustained_performance", true));
        assertTrue(prefs.getBoolean("checkbox_stretch_video", false));
        assertFalse(prefs.getBoolean("checkbox_enable_audiofx", true));
        assertTrue(prefs.getBoolean("checkbox_back_as_meta", false));
        assertTrue(prefs.getBoolean("checkbox_small_icon_mode", false));
        assertTrue(prefs.getBoolean("checkbox_disable_warnings", false));
        assertFalse(prefs.getBoolean("checkbox_enable_floating_button", true));
        assertFalse(prefs.getBoolean("checkbox_back_opens_menu", true));
        assertFalse(prefs.getBoolean("checkbox_touchscreen_trackpad", true));
        assertFalse(prefs.getBoolean("checkbox_pinch_to_zoom", true));
        assertFalse(prefs.getBoolean("checkbox_keyboard_vibrate", true));

        // User edits persist on subsequent calls
        prefs.edit()
                .putInt("seekbar_bitrate_kbps", 50000)
                .putString("list_fps", "120")
                .putString("vulkan_sharpening", "off")
                .putBoolean("checkbox_full_range", false)
                .commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertEquals(50000, prefs.getInt("seekbar_bitrate_kbps", 0));
        assertEquals("120", prefs.getString("list_fps", ""));
        assertEquals("off", prefs.getString("vulkan_sharpening", ""));
        assertFalse(prefs.getBoolean("checkbox_full_range", true));
    }

    @Test
    public void tvV1MigrationKeepsTheSelectedPacingVsyncAndAsyncModes() {
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
        assertEquals(1, prefs.getBoolean("mibox_tv_gaming_defaults_v3", false) ? 1 : 0);
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v4", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v5", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v6", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v7", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v8", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v9", false));
        assertFalse(prefs.getBoolean("checkbox_sustained_performance", true));
        assertTrue(prefs.getBoolean("checkbox_stretch_video", false));
        assertFalse(prefs.getBoolean("checkbox_enable_audiofx", true));
        assertTrue(prefs.getBoolean("checkbox_back_as_meta", false));
        assertTrue(prefs.getBoolean("checkbox_small_icon_mode", false));
        assertTrue(prefs.getBoolean("checkbox_disable_warnings", false));
        assertFalse(prefs.getBoolean("checkbox_exact_slices", true));
        assertEquals(20, prefs.getInt("seekbar_max_pending_audio_ms", 0));
        assertEquals("vulkan", prefs.getString("video_renderer", ""));
        assertEquals("high", prefs.getString("spatial_dithering", ""));
        assertEquals("balanced", prefs.getString("vulkan_sharpening", ""));
        assertTrue(prefs.getBoolean("checkbox_full_range", false));
        assertEquals("latency", prefs.getString("frame_pacing", ""));
        assertEquals("fast", prefs.getString("vsync_mode", ""));
        assertTrue(prefs.getBoolean("checkbox_async_decode", false));
        assertTrue(prefs.getBoolean("checkbox_async_lfr", false));
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
    public void tvDefaultsV2UpgradesCleanlyToV3() {
        prefs.edit()
                .putBoolean("mibox_tv_gaming_defaults_v1", true)
                .putBoolean("mibox_tv_gaming_defaults_v2", true)
                .putString("spatial_dithering", "low")
                .putInt("seekbar_bitrate_kbps", 35000)
                .commit();

        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);

        assertEquals(1, prefs.getBoolean("mibox_tv_gaming_defaults_v3", false) ? 1 : 0);
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v4", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v5", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v6", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v7", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v8", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v9", false));
        assertFalse(prefs.getBoolean("checkbox_sustained_performance", true));
        assertTrue(prefs.getBoolean("checkbox_stretch_video", false));
        assertFalse(prefs.getBoolean("checkbox_enable_audiofx", true));
        assertTrue(prefs.getBoolean("checkbox_back_as_meta", false));
        assertTrue(prefs.getBoolean("checkbox_small_icon_mode", false));
        assertTrue(prefs.getBoolean("checkbox_disable_warnings", false));
        assertFalse(prefs.getBoolean("checkbox_exact_slices", true));
        assertEquals(20, prefs.getInt("seekbar_max_pending_audio_ms", 0));
        assertEquals("low", prefs.getString("spatial_dithering", ""));
        assertEquals("balanced", prefs.getString("vulkan_sharpening", ""));
        assertTrue(prefs.getBoolean("checkbox_full_range", false));

        // User edits after v3 seeding are preserved
        prefs.edit()
                .putString("spatial_dithering", "off")
                .putString("vulkan_sharpening", "crisp")
                .putBoolean("checkbox_full_range", false)
                .commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertEquals("off", prefs.getString("spatial_dithering", ""));
        assertEquals("crisp", prefs.getString("vulkan_sharpening", ""));
        assertFalse(prefs.getBoolean("checkbox_full_range", true));
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
        assertEquals(240, operatingRate);

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

    @Test
    public void tvV4MigrationKeepsAnExplicitExactSlicesSetting() {
        prefs.edit()
                .putBoolean("mibox_tv_gaming_defaults_v1", true)
                .putBoolean("mibox_tv_gaming_defaults_v2", true)
                .putBoolean("mibox_tv_gaming_defaults_v3", true)
                .putBoolean("mibox_tv_gaming_defaults_v4", true)
                .putBoolean("checkbox_exact_slices", true)
                .commit();

        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);

        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v5", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v6", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v7", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v8", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v9", false));
        assertFalse(prefs.getBoolean("checkbox_sustained_performance", true));
        assertTrue(prefs.getBoolean("checkbox_stretch_video", false));
        assertFalse(prefs.getBoolean("checkbox_enable_audiofx", true));
        assertTrue(prefs.getBoolean("checkbox_back_as_meta", false));
        assertTrue(prefs.getBoolean("checkbox_small_icon_mode", false));
        assertTrue(prefs.getBoolean("checkbox_disable_warnings", false));
        assertTrue(prefs.getBoolean("checkbox_exact_slices", false));
        assertEquals(20, prefs.getInt("seekbar_max_pending_audio_ms", 0));

        // Explicit user changes after v5 migration persist
        prefs.edit()
                .putBoolean("checkbox_exact_slices", true)
                .putInt("seekbar_max_pending_audio_ms", 15)
                .commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertTrue(prefs.getBoolean("checkbox_exact_slices", false));
        assertEquals(15, prefs.getInt("seekbar_max_pending_audio_ms", 0));
    }

    @Test
    public void amlogicHevcEnablesRfiOnModernCodec2AndBlocksLegacyOmx() {
        assertTrue(MediaCodecHelper.shouldAllowHevcRfiForDecoder("c2.amlogic.hevc.decoder", false));
        assertFalse(MediaCodecHelper.shouldAllowHevcRfiForDecoder("OMX.amlogic.hevc.decoder.awesome", false));
        assertTrue(MediaCodecHelper.shouldAllowHevcRfiForDecoder("OMX.amlogic.hevc.decoder.awesome", true));
        assertTrue(MediaCodecHelper.shouldAllowHevcRfiForDecoder("c2.qti.hevc.decoder", false));

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
        assertTrue(MediaCodecHelper.decoderSupportsRefFrameInvalidationHevc(amlogic));
    }

    @Test
    public void fecPercentageAndStreamConfigurationDefaults() {
        assertEquals(-1, PreferenceConfiguration.parseFecPercentage(null));
        assertEquals(-1, PreferenceConfiguration.parseFecPercentage("-1"));
        assertEquals(0, PreferenceConfiguration.parseFecPercentage("0"));
        assertEquals(20, PreferenceConfiguration.parseFecPercentage("20"));
        assertEquals(255, PreferenceConfiguration.parseFecPercentage("999"));
        assertEquals(-1, PreferenceConfiguration.parseFecPercentage("invalid"));

        StreamConfiguration defaults = new StreamConfiguration.Builder().build();
        assertEquals(-1, defaults.getFecPercentage());
        assertFalse(defaults.getExactSlicesPerFrame());
        assertEquals(0, defaults.getClientRefreshRateUhz());
    }

    @Test
    public void decoderInputBufferSizingProvidesSafeHevcFloorAndExpands() {
        int hevcSize = DecoderInputBufferSizing.initialInputBufferSize("video/hevc", 1920, 1080, 35000);
        int avcSize = DecoderInputBufferSizing.initialInputBufferSize("video/avc", 1280, 720, 10000);
        assertTrue(hevcSize >= DecoderInputBufferSizing.MIN_HEVC_INPUT_BYTES);
        assertTrue(avcSize >= DecoderInputBufferSizing.MIN_AVC_AV1_INPUT_BYTES);

        int expanded = DecoderInputBufferSizing.expandedInputBufferSize(hevcSize, hevcSize + 512 * 1024);
        assertTrue(expanded >= hevcSize * 2);
        assertEquals(-1, DecoderInputBufferSizing.expandedInputBufferSize(
                DecoderInputBufferSizing.MAX_INPUT_BYTES, DecoderInputBufferSizing.MAX_INPUT_BYTES + 1));
    }

    @Test
    public void controllerStateSenderDeduplicatesAndHonorsKeepaliveAndForce() {
        RecordingConnection conn = new RecordingConnection(context);
        ControllerStateSender sender = new ControllerStateSender();
        long t0 = 10_000_000_000L;

        assertTrue(sender.sendIfNeeded(conn, (short) 0, (short) 1, 0, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, t0, false));
        assertFalse(sender.sendIfNeeded(conn, (short) 0, (short) 1, 0, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, t0 + 10_000_000L, false));
        assertTrue(sender.sendIfNeeded(conn, (short) 0, (short) 1, 0x1000, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, t0 + 10_000_000L, false));
        assertFalse(sender.sendIfNeeded(conn, (short) 0, (short) 1, 0x1000, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, t0 + 500_000_000L, false));
        assertTrue(sender.sendIfNeeded(conn, (short) 0, (short) 1, 0x1000, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, t0 + 10_000_000L + ControllerStateSender.KEEPALIVE_INTERVAL_NS, false));
        assertTrue(sender.sendIfNeeded(conn, (short) 0, (short) 1, 0x1000, (byte) 0, (byte) 0,
                (short) 0, (short) 0, (short) 0, (short) 0, t0 + 10_000_000L + ControllerStateSender.KEEPALIVE_INTERVAL_NS, true));
        assertEquals(4, conn.sent.size());
    }

    @Test
    public void stickCenterStoreRecentersDriftWhilePreservingFullTravel() {
        assertEquals(0f, StickCenterStore.applyCenterOffset(0.08f, 0.08f), 1e-5f);
        assertEquals(1f, StickCenterStore.applyCenterOffset(1f, 0.08f), 1e-5f);
        assertEquals(-1f, StickCenterStore.applyCenterOffset(-1f, 0.08f), 1e-5f);
        assertEquals(0f, StickCenterStore.applyCenterOffset(-0.10f, -0.10f), 1e-5f);
        assertEquals(1f, StickCenterStore.applyCenterOffset(1f, -0.10f), 1e-5f);

        StickCenterStore store = new StickCenterStore(context);
        store.save(0x045e, 0x02ea, "usb-dongle", 0.05f, -0.04f, 0.40f, -0.02f);
        StickCenterStore.Calibration loaded = store.load(0x045e, 0x02ea, "usb-dongle");
        assertEquals(0.05f, loaded.lx, 1e-5f);
        assertEquals(-0.04f, loaded.ly, 1e-5f);
        assertEquals(StickCenterStore.MAX_OFFSET, loaded.rx, 1e-5f);
        assertEquals(-0.02f, loaded.ry, 1e-5f);
    }

    @Test
    public void pacingStatsComputesRollingPercentilesAndIgnoresOutliers() {
        PacingStats stats = new PacingStats();
        long t = 1_000_000_000L;
        stats.recordPresent(t);
        for (int i = 0; i < 98; i++) {
            t += 16_670_000L;
            stats.recordPresent(t);
        }
        for (int i = 0; i < 2; i++) {
            t += 33_330_000L;
            stats.recordPresent(t);
        }
        // >= 1 s pause gap must be ignored
        t += 1_500_000_000L;
        stats.recordPresent(t);

        PacingStats.Snapshot snap = stats.snapshot();
        assertEquals(100, snap.sampleCount);
        assertEquals(16.67f, snap.p50Ms, 0.05f);
        assertEquals(16.67f, snap.p95Ms, 0.05f);
        assertEquals(33.33f, snap.p99Ms, 0.05f);
    }

    @Test
    public void pacingStatsIgnoresHostStaticScreenIdlePauses() {
        PacingStats stats = new PacingStats();
        long presentNs = 1_000_000_000L;
        long hostPtsUs = 0L;

        stats.recordPresent(presentNs, hostPtsUs, 60);
        for (int i = 0; i < 18; i++) {
            presentNs += 16_670_000L;
            hostPtsUs += 16_667L;
            stats.recordPresent(presentNs, hostPtsUs, 60);
        }
        // Static-screen / 3-refresh desktop idle pauses (50.0 ms, 116.7 ms, and 133.3 ms host PTS jumps)
        // must not pollute p95/p99
        presentNs += 50_000_000L;
        hostPtsUs += 49_980L;
        stats.recordPresent(presentNs, hostPtsUs, 60);
        presentNs += 116_700_000L;
        hostPtsUs += 116_667L;
        stats.recordPresent(presentNs, hostPtsUs, 60);
        presentNs += 133_300_000L;
        hostPtsUs += 133_333L;
        stats.recordPresent(presentNs, hostPtsUs, 60);
        // Real 33.3 ms client miss when host advanced by 1 frame (16.67 ms) IS recorded
        presentNs += 33_330_000L;
        hostPtsUs += 16_667L;
        stats.recordPresent(presentNs, hostPtsUs, 60);

        PacingStats.Snapshot snap = stats.snapshot();
        assertEquals(19, snap.sampleCount);
        assertEquals(16.67f, snap.p50Ms, 0.05f);
        assertEquals(33.33f, snap.p99Ms, 0.05f);
    }

    @Test
    public void adpfHelperClampsWorkAndDeduplicatesTids() {
        assertEquals(0L, AdpfHelper.clampWorkDurationNs(0L, 3_000_000L));
        assertEquals(0L, AdpfHelper.clampWorkDurationNs(-500L, 3_000_000L));
        assertEquals(AdpfHelper.MIN_REPORT_WORK_NS, AdpfHelper.clampWorkDurationNs(10_000L, 3_000_000L));
        assertEquals(2_500_000L, AdpfHelper.clampWorkDurationNs(2_500_000L, 3_000_000L));
        assertEquals(12_000_000L, AdpfHelper.clampWorkDurationNs(50_000_000L, 3_000_000L));

        int[] tids = new int[0];
        tids = AdpfHelper.appendUniqueTid(tids, 101);
        assertEquals(1, tids.length);
        assertTrue(tids == AdpfHelper.appendUniqueTid(tids, 101));
        assertTrue(tids == AdpfHelper.appendUniqueTid(tids, -1));
        tids = AdpfHelper.appendUniqueTid(tids, 202);
        assertEquals(2, tids.length);
        assertEquals(202, tids[1]);
    }

    @Test
    public void audioRendererClampsMaxPendingAudioMs() {
        assertEquals(10, AndroidAudioRenderer.clampMaxPendingAudioMs(5));
        assertEquals(20, AndroidAudioRenderer.clampMaxPendingAudioMs(20));
        assertEquals(100, AndroidAudioRenderer.clampMaxPendingAudioMs(250));
    }

    @Test
    public void tvV7MigrationKeepsAudioEffectsAndHonorsTheirLatencyTradeoff() {
        prefs.edit()
                .putBoolean("mibox_tv_gaming_defaults_v1", true)
                .putBoolean("mibox_tv_gaming_defaults_v2", true)
                .putBoolean("mibox_tv_gaming_defaults_v3", true)
                .putBoolean("mibox_tv_gaming_defaults_v4", true)
                .putBoolean("mibox_tv_gaming_defaults_v5", true)
                .putBoolean("mibox_tv_gaming_defaults_v6", true)
                .putBoolean("mibox_tv_gaming_defaults_v7", true)
                .putBoolean("checkbox_enable_audiofx", true)
                .commit();

        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);

        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v8", false));
        boolean audioFx = prefs.getBoolean("checkbox_enable_audiofx", true);
        assertTrue(audioFx);
        assertFalse(AndroidAudioRenderer.shouldAttemptLowLatencyAudio(34, 48000, 48000, audioFx));

        // Opting out later reopens low-latency audio.
        prefs.edit().putBoolean("checkbox_enable_audiofx", false).commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertFalse(prefs.getBoolean("checkbox_enable_audiofx", true));
        assertTrue(AndroidAudioRenderer.shouldAttemptLowLatencyAudio(34, 48000, 48000, false));
    }

    @Test
    public void audioRendererHalBurstSizingTrimsHdmiAndSkipsBluetooth() {
        assertEquals(480, AndroidAudioRenderer.computeLowLatencyBufferFrames(240, 240, 2400, true, false));
        assertEquals(600, AndroidAudioRenderer.computeLowLatencyBufferFrames(480, 240, 600, true, false));
        assertEquals(120, AndroidAudioRenderer.computeLowLatencyBufferFrames(480, 240, 120, true, false));
        assertEquals(600, AndroidAudioRenderer.computeLowLatencyBufferFrames(Integer.MAX_VALUE, 240, 600, true, false));
        assertEquals(-1, AndroidAudioRenderer.computeLowLatencyBufferFrames(240, 240, 2400, true, true));
        assertEquals(-1, AndroidAudioRenderer.computeLowLatencyBufferFrames(240, 240, 2400, false, false));
        assertTrue(AndroidAudioRenderer.isBluetoothAudioDeviceType(34, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertTrue(AndroidAudioRenderer.isBluetoothAudioDeviceType(34, AudioDeviceInfo.TYPE_BLE_HEADSET));
        assertFalse(AndroidAudioRenderer.isBluetoothAudioDeviceType(34, AudioDeviceInfo.TYPE_HDMI));
    }

    @Test
    public void hardwareAv1WhitelistAndAmlogicProgressiveFallbacksWork() {
        assertTrue(MediaCodecHelper.isHardwareAv1DecoderWhitelisted(34, "c2.amlogic.av1.decoder", true, false, false));
        assertFalse(MediaCodecHelper.isHardwareAv1DecoderWhitelisted(34, "c2.android.av1-dav1d.decoder", false, true, false));
        assertFalse(MediaCodecHelper.isHardwareAv1DecoderWhitelisted(34, "OMX.google.av1.decoder.sw", true, false, true));
        assertTrue(MediaCodecHelper.isHardwareAv1DecoderWhitelisted(34, "c2.qti.av1.decoder", true, false, true));

        assertEquals(240, MediaCodecHelper.computeAmlogicOperatingRate(60, 1920, 1080, -1));
        assertEquals(75, MediaCodecHelper.computeAmlogicOperatingRate(60, 3840, 2160, 75.0));
        assertEquals(120, MediaCodecHelper.computeAmlogicOperatingRate(60, 3840, 2160, -1));

        MediaCodecHelper.initialize(context, "Mali-G310");
        CodecProfileLevel av1Profile = new CodecProfileLevel();
        av1Profile.profile = CodecProfileLevel.AV1ProfileMain10;
        av1Profile.level = CodecProfileLevel.AV1Level51;
        MediaCodecInfo amlogic = MediaCodecInfoBuilder.newBuilder()
                .setName("c2.amlogic.av1.decoder")
                .setIsSoftwareOnly(false)
                .setIsHardwareAccelerated(true)
                .setCapabilities(MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                        .setMediaFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AV1, 3840, 2160))
                        .setIsEncoder(false)
                        .setProfileLevels(new CodecProfileLevel[] {av1Profile})
                        .setColorFormats(new int[] {CodecCapabilities.COLOR_FormatYUV420Flexible})
                        .setRequiredFeatures(new String[] {CodecCapabilities.FEATURE_LowLatency})
                        .build())
                .build();

        MediaFormat try1 = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AV1, 3840, 2160);
        try1.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
        assertTrue(MediaCodecHelper.setDecoderLowLatencyOptions(try1, amlogic, false, 1));
        assertEquals(1, try1.getInteger("vendor.low-latency.enable"));
        assertEquals(1, try1.getInteger("vendor.amlogic.vdec.low-latency"));
        assertEquals(0, try1.getInteger(MediaFormat.KEY_PRIORITY));
        assertFalse(try1.containsKey(MediaFormat.KEY_OPERATING_RATE));
        assertFalse(try1.containsKey("vendor.media.omx.gamemode"));

        MediaFormat try2 = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AV1, 3840, 2160);
        assertTrue(MediaCodecHelper.setDecoderLowLatencyOptions(try2, amlogic, false, 2));
        assertEquals(1, try2.getInteger("vendor.low-latency.enable"));
        assertEquals(0, try2.getInteger(MediaFormat.KEY_PRIORITY));
        assertFalse(try2.containsKey("vendor.amlogic.vdec.low-latency"));

        MediaFormat try3 = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AV1, 3840, 2160);
        assertTrue(MediaCodecHelper.setDecoderLowLatencyOptions(try3, amlogic, false, 3));
        assertEquals(0, try3.getInteger(MediaFormat.KEY_PRIORITY));
        assertFalse(try3.containsKey("vendor.low-latency.enable"));

        MediaFormat try4 = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AV1, 3840, 2160);
        assertFalse(MediaCodecHelper.setDecoderLowLatencyOptions(try4, amlogic, false, 4));
    }

    @Test
    public void secondaryCoverCacheHasABoundedKeyBudget() throws Exception {
        java.lang.reflect.Field cache = MemoryAssetLoader.class.getDeclaredField("evictionCache");
        cache.setAccessible(true);
        android.util.LruCache<?, ?> secondary = (android.util.LruCache<?, ?>) cache.get(null);
        assertEquals(64, secondary.maxSize());
        MemoryAssetLoader.clearStaticCache();
        assertEquals(0, secondary.size());
    }

    @Test
    public void smallCoversUseCacheBudgetAndClearAcrossInstances() throws Exception {
        ComputerDetails host = new ComputerDetails();
        host.uuid = "cache-test";
        CachedAppAssetLoader.LoaderTuple tuple = new CachedAppAssetLoader.LoaderTuple(host, new NvApp("Card", 3, false));
        MemoryAssetLoader loader = new MemoryAssetLoader();
        MemoryAssetLoader.clearStaticCache();
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        ScaledBitmap cover = new ScaledBitmap(2, 2, bitmap);
        loader.populateCache(tuple, cover);
        java.lang.reflect.Field cache = MemoryAssetLoader.class.getDeclaredField("memoryCache");
        cache.setAccessible(true);
        assertEquals(1, ((android.util.LruCache<?, ?>) cache.get(null)).size());
        assertEquals(cover, new MemoryAssetLoader().loadBitmapFromCache(tuple));
        new MemoryAssetLoader().clearCache();
        org.junit.Assert.assertNull(loader.loadBitmapFromCache(tuple));
    }

    @Test
    public void displayHdr10WiredNetworkAndMemoryTrimHelpersWork() {
        assertTrue(Game.isDisplayHdr10Capable(new int[] {Display.HdrCapabilities.HDR_TYPE_HDR10}));
        assertTrue(Game.isDisplayHdr10Capable(new int[] {Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS}));
        assertFalse(Game.isDisplayHdr10Capable(new int[] {Display.HdrCapabilities.HDR_TYPE_HLG}));
        assertFalse(Game.isDisplayHdr10Capable(null));

        assertTrue(Game.isWiredEthernetOnly(true, false));
        assertFalse(Game.isWiredEthernetOnly(true, true));
        assertFalse(Game.isWiredEthernetOnly(false, true));

        AppIcons.clearCache();
        MemoryAssetLoader.clearStaticCache();
    }

    @Test
    public void tvDefaultsV8UpgradesCleanlyToV9AndRespectsSustainedPerformanceToggle() {
        prefs.edit()
                .putBoolean("mibox_tv_gaming_defaults_v1", true)
                .putBoolean("mibox_tv_gaming_defaults_v2", true)
                .putBoolean("mibox_tv_gaming_defaults_v3", true)
                .putBoolean("mibox_tv_gaming_defaults_v4", true)
                .putBoolean("mibox_tv_gaming_defaults_v5", true)
                .putBoolean("mibox_tv_gaming_defaults_v6", true)
                .putBoolean("mibox_tv_gaming_defaults_v7", true)
                .putBoolean("mibox_tv_gaming_defaults_v8", true)
                .putBoolean("checkbox_sustained_performance", true)
                .putBoolean("checkbox_enable_audiofx", true)
                .commit();

        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);

        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v9", false));
        assertTrue(prefs.getBoolean("checkbox_sustained_performance", false));
        assertTrue(prefs.getBoolean("checkbox_enable_audiofx", false));

        PreferenceConfiguration offCfg = new PreferenceConfiguration();
        offCfg.enableSustainedPerformance = false;
        assertFalse(Game.shouldEnableSustainedPerformance(offCfg));

        prefs.edit().putBoolean("checkbox_sustained_performance", true).commit();
        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);
        assertTrue(prefs.getBoolean("checkbox_sustained_performance", false));

        PreferenceConfiguration onCfg = new PreferenceConfiguration();
        onCfg.enableSustainedPerformance = true;
        assertTrue(Game.shouldEnableSustainedPerformance(onCfg));
    }

    @Test
    public void ghostOverlayActiveOnlyOnTvWhenOverlayHiddenAndTogglesTextWithoutStatsWork() {
        assertTrue(Game.isGhostOverlayActive(true, false, false));
        assertTrue(Game.isGhostOverlayActive(true, true, true));
        assertFalse(Game.isGhostOverlayActive(true, true, false));
        assertFalse(Game.isGhostOverlayActive(false, false, false));
        assertFalse(Game.ghostOverlayText(false).equals(Game.ghostOverlayText(true)));
        assertEquals(Game.ghostOverlayText(false).length(), Game.ghostOverlayText(true).length());
    }

    @Test
    public void extendedDynamicRangeSupportsHlgHdr10Hdr10PlusDolbyVisionAndCompatMode() {
        assertTrue(Game.isDisplayExtendedHdrCapable(new int[] {Display.HdrCapabilities.HDR_TYPE_HDR10}));
        assertTrue(Game.isDisplayExtendedHdrCapable(new int[] {Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS}));
        assertTrue(Game.isDisplayExtendedHdrCapable(new int[] {Display.HdrCapabilities.HDR_TYPE_HLG}));
        assertTrue(Game.isDisplayExtendedHdrCapable(new int[] {Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION}));
        assertFalse(Game.isDisplayExtendedHdrCapable(null));
        assertFalse(Game.isDisplayExtendedHdrCapable(new int[0]));

        assertEquals("SDR", Game.describeDisplayHdrTypes(null));
        assertEquals("HDR10, HDR10+, HLG, Dolby Vision", Game.describeDisplayHdrTypes(new int[] {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION,
                Display.HdrCapabilities.HDR_TYPE_HLG,
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
                Display.HdrCapabilities.HDR_TYPE_HDR10
        }));

        // Default SDR panel stays SDR unless compat mode is turned on alongside HDR
        assertFalse(Game.shouldAllowHdrStream(false, new int[] {Display.HdrCapabilities.HDR_TYPE_HDR10}, true));
        assertFalse(Game.shouldAllowHdrStream(true, null, false));
        assertTrue(Game.shouldAllowHdrStream(true, null, true));
        assertTrue(Game.shouldAllowHdrStream(true, new int[] {Display.HdrCapabilities.HDR_TYPE_HLG}, false));
        assertTrue(Game.shouldAllowHdrStream(true, new int[] {Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION}, false));

        // Only request Window COLOR_MODE_HDR when the physical display advertises an HDR format
        assertEquals(ActivityInfo.COLOR_MODE_HDR,
                Game.chooseWindowColorMode(true, new int[] {Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION}));
        assertEquals(ActivityInfo.COLOR_MODE_DEFAULT,
                Game.chooseWindowColorMode(true, null));

        // Codec profile recognition for HDR10 and HDR10+ (plus AV1 Main10 compat fallback)
        assertTrue(MediaCodecDecoderRenderer.isHevcHdrProfileSupported(
                CodecProfileLevel.HEVCProfileMain10HDR10, Build.VERSION_CODES.Q));
        assertTrue(MediaCodecDecoderRenderer.isHevcHdrProfileSupported(
                CodecProfileLevel.HEVCProfileMain10HDR10Plus, Build.VERSION_CODES.Q));
        assertTrue(MediaCodecDecoderRenderer.isAv1HdrProfileSupported(
                CodecProfileLevel.AV1ProfileMain10HDR10, Build.VERSION_CODES.Q, false));
        assertTrue(MediaCodecDecoderRenderer.isAv1HdrProfileSupported(
                CodecProfileLevel.AV1ProfileMain10HDR10Plus, Build.VERSION_CODES.Q, false));
        assertFalse(MediaCodecDecoderRenderer.isAv1HdrProfileSupported(
                CodecProfileLevel.AV1ProfileMain10, Build.VERSION_CODES.Q, false));
        assertTrue(MediaCodecDecoderRenderer.isAv1HdrProfileSupported(
                CodecProfileLevel.AV1ProfileMain10, Build.VERSION_CODES.Q, true));
    }

    @Test
    public void audioCapabilitiesAndAutoChannelSelectionPreserveDefaultsAndSupportSurround() {
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO,
                AndroidAudioRenderer.selectBestAudioConfigForChannelCounts(null));
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO,
                AndroidAudioRenderer.selectBestAudioConfigForChannelCounts(new int[] {2}));
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_51_SURROUND,
                AndroidAudioRenderer.selectBestAudioConfigForChannelCounts(new int[] {2, 6}));
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_71_SURROUND,
                AndroidAudioRenderer.selectBestAudioConfigForChannelCounts(new int[] {2, 6, 8}));

        assertTrue(AndroidAudioRenderer.isPrioritizedExternalAudioDevice(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, AudioDeviceInfo.TYPE_HDMI));
        assertTrue(AndroidAudioRenderer.isPrioritizedExternalAudioDevice(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, AudioDeviceInfo.TYPE_HDMI_EARC));
        assertTrue(AndroidAudioRenderer.isPrioritizedExternalAudioDevice(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, AudioDeviceInfo.TYPE_USB_HEADSET));
        assertFalse(AndroidAudioRenderer.isPrioritizedExternalAudioDevice(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
        assertFalse(AndroidAudioRenderer.isPrioritizedExternalAudioDevice(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));

        // Default config stays standard 2.0 Stereo + 16-bit PCM + HDR off
        PreferenceConfiguration defaults = PreferenceConfiguration.readPreferences(context);
        assertEquals(MoonBridge.AUDIO_CONFIGURATION_STEREO, defaults.audioConfiguration);
        assertFalse(defaults.hdrCompatMode);
        assertFalse(defaults.preferFloatPcmAudio);

        // Opt-in toggles persist cleanly
        prefs.edit()
                .putString("list_audio_config", "auto")
                .putBoolean("checkbox_hdr_compat", true)
                .putBoolean("checkbox_float_pcm_audio", true)
                .commit();
        PreferenceConfiguration optedIn = PreferenceConfiguration.readPreferences(context);
        assertTrue(optedIn.hdrCompatMode);
        assertTrue(optedIn.preferFloatPcmAudio);
        assertTrue(AndroidAudioRenderer.describeAudioCapabilities(context).contains("PCM"));

        // Float PCM path drives AudioHaptics without int16 conversion
        AudioHaptics haptics = new AudioHaptics(context, AudioHaptics.OUTPUT_CONTROLLER, 100, null);
        haptics.setup(48000, 2);
        float[] bassPulse = new float[480];
        for (int i = 0; i < bassPulse.length; i += 2) {
            bassPulse[i] = 0.5f;
            bassPulse[i + 1] = 0.5f;
        }
        haptics.process(bassPulse);
    }

    @Test
    public void menuShellOptimizationsAndHelpersWorkDeterministically() throws Exception {
        assertTrue(BuildConfig.VERSION_CODE >= 322);

        String appKey = "pc-1:42";
        assertFalse(AppPreferences.hasCustomSettings(context, appKey));

        AppPreferences.AppSettings globalSettings = new AppPreferences.AppSettings();
        globalSettings.useGlobalSettings = true;
        AppPreferences.saveAppSettings(context, appKey, globalSettings);
        assertFalse(AppPreferences.hasCustomSettings(context, appKey));

        AppPreferences.AppSettings customSettings = new AppPreferences.AppSettings(
                "1920x1080", 60, "mibox-60hz", 35000, 60.0, "false", "false", false);
        AppPreferences.saveAppSettings(context, appKey, customSettings);
        assertTrue(AppPreferences.hasCustomSettings(context, appKey));

        assertEquals(0xFF112233, CoverGlow.rememberedTintOrDefault(context, "pc-1", 42, 0xFF112233));
        CoverGlow.rememberTint(context, "pc-1", 42, 0xFFFF6700);
        assertEquals(0xFFFF6700, CoverGlow.rememberedTintOrDefault(context, "pc-1", 42, 0xFF112233));

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.outWidth = 1200;
        opts.outHeight = 1600;
        assertEquals(4, DiskAssetLoader.calculateInSampleSize(opts, 300, 400));
        opts.outWidth = 300;
        opts.outHeight = 400;
        assertEquals(1, DiskAssetLoader.calculateInSampleSize(opts, 300, 400));

        ComputerDetails pc = new ComputerDetails();
        pc.uuid = "pc-1";
        DiskAssetLoader diskLoader = new DiskAssetLoader(context);
        File cachedArt = diskLoader.getFile(pc.uuid, 42);
        if (cachedArt.getParentFile() != null) {
            cachedArt.getParentFile().mkdirs();
        }
        assertTrue(cachedArt.exists() || cachedArt.createNewFile());

        CachedAppAssetLoader loader = new CachedAppAssetLoader(
                pc,
                1.0,
                new NetworkAssetLoader(context, "0123456789ABCDEF"),
                new MemoryAssetLoader(),
                diskLoader);
        loader.shutdown();

        PreferenceConfiguration prefs = PreferenceConfiguration.readPreferences(context);
        AppGridAdapter adapter = new AppGridAdapter(context, prefs, pc, "0123456789ABCDEF", false);
        adapter.addApp(new AppView.AppObject(new NvApp("Desktop", 42, false)));
        assertEquals(1, adapter.getCount());

        FrameLayout parent = new FrameLayout(context);
        View row1 = adapter.getView(0, null, parent);
        View row2 = adapter.getView(0, row1, parent);
        assertEquals(row1, row2);

        adapter.setSelectedAppId(42);
        adapter.setSelectedAppId(42);
        adapter.invalidateCustomSettingsCache();
        adapter.shutdown();
    }
}
