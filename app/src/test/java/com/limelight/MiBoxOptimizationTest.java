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
import com.limelight.binding.input.ControllerStateSender;
import com.limelight.binding.input.StickCenterStore;
import com.limelight.binding.video.AdpfHelper;
import com.limelight.binding.video.DecoderInputBufferSizing;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PacingStats;
import com.limelight.nvstream.StreamConfiguration;
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
        assertEquals(1, prefs.getBoolean("mibox_tv_gaming_defaults_v3", false) ? 1 : 0);
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v4", false));
        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v5", false));
        assertFalse(prefs.getBoolean("checkbox_exact_slices", true));
        assertEquals(20, prefs.getInt("seekbar_max_pending_audio_ms", 0));
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
        assertFalse(prefs.getBoolean("checkbox_exact_slices", true));
        assertEquals(20, prefs.getInt("seekbar_max_pending_audio_ms", 0));
        assertEquals("high", prefs.getString("spatial_dithering", ""));
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
    public void tvDefaultsV4UpgradesCleanlyToV5AndDisablesExactSlices() {
        prefs.edit()
                .putBoolean("mibox_tv_gaming_defaults_v1", true)
                .putBoolean("mibox_tv_gaming_defaults_v2", true)
                .putBoolean("mibox_tv_gaming_defaults_v3", true)
                .putBoolean("mibox_tv_gaming_defaults_v4", true)
                .putBoolean("checkbox_exact_slices", true)
                .commit();

        PreferenceConfiguration.seedTvGamingDefaultsIfNeeded(prefs, true);

        assertTrue(prefs.getBoolean("mibox_tv_gaming_defaults_v5", false));
        assertFalse(prefs.getBoolean("checkbox_exact_slices", true));
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
}
