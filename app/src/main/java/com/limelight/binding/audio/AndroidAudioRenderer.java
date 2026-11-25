package com.limelight.binding.audio;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.Spatializer;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

import java.util.LinkedHashSet;
import java.util.Set;

public class AndroidAudioRenderer implements AudioRenderer {

    public static final int DEFAULT_MAX_PENDING_AUDIO_MS = 30;
    public static final String AUDIO_CONFIG_AUTO = "auto";

    private final Context context;
    private final boolean enableAudioFx;
    private final int maxPendingAudioMs;
    private final boolean preferFloatPcm;

    private AudioTrack track;
    private boolean floatPcmActive;
    private boolean audioThreadPriorityElevated;

    // Optional: turns the bass in the audio into vibration
    private final AudioHaptics haptics;

    public static int clampMaxPendingAudioMs(int ms) {
        return Math.max(10, Math.min(100, ms));
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx) {
        this(context, enableAudioFx, null, DEFAULT_MAX_PENDING_AUDIO_MS, false);
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx, AudioHaptics haptics) {
        this(context, enableAudioFx, haptics, DEFAULT_MAX_PENDING_AUDIO_MS, false);
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx, AudioHaptics haptics, int maxPendingAudioMs) {
        this(context, enableAudioFx, haptics, maxPendingAudioMs, false);
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx, AudioHaptics haptics,
                                int maxPendingAudioMs, boolean preferFloatPcm) {
        this.context = context;
        this.enableAudioFx = enableAudioFx;
        this.haptics = haptics;
        this.maxPendingAudioMs = clampMaxPendingAudioMs(maxPendingAudioMs);
        this.preferFloatPcm = preferFloatPcm;
    }

    private AudioTrack createAudioTrack(int encoding, int channelConfig, int sampleRate, int bufferSize, boolean lowLatency) {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME);
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Use FLAG_LOW_LATENCY on L through N
            if (lowLatency) {
                attributesBuilder.setFlags(AudioAttributes.FLAG_LOW_LATENCY);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioTrack.Builder trackBuilder = new AudioTrack.Builder()
                    .setAudioFormat(format)
                    .setAudioAttributes(attributesBuilder.build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferSize);

            // Use PERFORMANCE_MODE_LOW_LATENCY on O and later
            if (lowLatency) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }

            return trackBuilder.build();
        }
        else {
            return new AudioTrack(attributesBuilder.build(),
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
        }
    }

    public static boolean shouldAttemptLowLatencyAudio(int sdkInt, int nativeSampleRate,
                                                       int streamSampleRate, boolean enableAudioFx) {
        if (enableAudioFx) {
            return false;
        }
        // On Android 12+ (API 31+, including Android 14 TV), AudioTrack PERFORMANCE_MODE_LOW_LATENCY
        // supports resampling via FastMixer even when HDMI initially reports 44.1 kHz before switching to 48 kHz.
        return nativeSampleRate == streamSampleRate || sdkInt >= Build.VERSION_CODES.S;
    }

    /**
     * Computes a tight playback threshold in PCM frames for AudioTrack.setBufferSizeInFrames()
     * using the HAL burst size (PROPERTY_OUTPUT_FRAMES_PER_BUFFER) on low-latency wired/HDMI outputs.
     * Returns -1 when buffer trimming should not be applied (standard mode or Bluetooth output).
     */
    public static int computeLowLatencyBufferFrames(int halFramesPerBurst, int packetFrames,
                                                    int capacityFrames, boolean lowLatency,
                                                    boolean bluetoothOutput) {
        if (!lowLatency || bluetoothOutput || capacityFrames <= 0) {
            return -1;
        }
        int safePacketFrames = Math.max(1, packetFrames);
        int burstFrames = halFramesPerBurst > 0 ? halFramesPerBurst : Math.max(240, safePacketFrames);
        int minSafeFrames = Math.max(safePacketFrames, burstFrames);
        int desiredFrames = Math.max(safePacketFrames * 2, burstFrames * 2);
        return Math.max(minSafeFrames, Math.min(desiredFrames, capacityFrames));
    }

    public static boolean isBluetoothAudioDeviceType(int sdkInt, int deviceType) {
        if (sdkInt < Build.VERSION_CODES.M || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (sdkInt >= Build.VERSION_CODES.P && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                deviceType == AudioDeviceInfo.TYPE_HEARING_AID) {
            return true;
        }
        return sdkInt >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER);
    }

    private static int parsePositiveInt(String raw) {
        if (raw == null || raw.isEmpty()) {
            return -1;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean isBluetoothOutput(AudioTrack audioTrack, AudioManager audioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && audioTrack != null) {
            AudioDeviceInfo routed = audioTrack.getRoutedDevice();
            if (routed != null) {
                return isBluetoothAudioDeviceType(Build.VERSION.SDK_INT, routed.getType());
            }
        }
        return audioManager != null && (audioManager.isBluetoothA2dpOn() || audioManager.isBluetoothScoOn());
    }

    public static boolean isPrioritizedExternalAudioDevice(int sdkInt, int deviceType) {
        if (sdkInt < Build.VERSION_CODES.M || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }
        if (deviceType == AudioDeviceInfo.TYPE_HDMI ||
                deviceType == AudioDeviceInfo.TYPE_HDMI_ARC ||
                deviceType == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
                deviceType == AudioDeviceInfo.TYPE_USB_DEVICE ||
                deviceType == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                deviceType == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
            return true;
        }
        if (sdkInt >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                deviceType == AudioDeviceInfo.TYPE_USB_HEADSET) {
            return true;
        }
        return sdkInt >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                deviceType == AudioDeviceInfo.TYPE_HDMI_EARC;
    }

    public static MoonBridge.AudioConfiguration selectBestAudioConfigForChannelCounts(int[] channelCounts) {
        int maxChannels = 2;
        if (channelCounts != null) {
            for (int count : channelCounts) {
                if (count > maxChannels) {
                    maxChannels = count;
                }
            }
        }
        if (maxChannels >= 8) {
            return MoonBridge.AUDIO_CONFIGURATION_71_SURROUND;
        }
        if (maxChannels >= 6) {
            return MoonBridge.AUDIO_CONFIGURATION_51_SURROUND;
        }
        return MoonBridge.AUDIO_CONFIGURATION_STEREO;
    }

    public static MoonBridge.AudioConfiguration resolveAutoAudioConfiguration(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return MoonBridge.AUDIO_CONFIGURATION_STEREO;
        }
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                return MoonBridge.AUDIO_CONFIGURATION_STEREO;
            }
            AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            if (outputs == null || outputs.length == 0) {
                return MoonBridge.AUDIO_CONFIGURATION_STEREO;
            }

            int bestExternal = 0;
            int bestAny = 2;
            for (AudioDeviceInfo dev : outputs) {
                int[] counts = dev.getChannelCounts();
                if (counts == null) {
                    continue;
                }
                boolean external = isPrioritizedExternalAudioDevice(Build.VERSION.SDK_INT, dev.getType());
                for (int c : counts) {
                    if (c > bestAny) {
                        bestAny = c;
                    }
                    if (external && c > bestExternal) {
                        bestExternal = c;
                    }
                }
            }
            int chosen = bestExternal > 0 ? bestExternal : bestAny;
            return selectBestAudioConfigForChannelCounts(new int[] { chosen });
        } catch (Throwable ignored) {
            return MoonBridge.AUDIO_CONFIGURATION_STEREO;
        }
    }

    public static String describeAudioCapabilities(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return "PCM 2.0 Stereo";
        }
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager == null) {
                return "PCM 2.0 Stereo";
            }
            AudioDeviceInfo[] outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            int maxPcmChannels = 2;
            Set<String> pcmFormats = new LinkedHashSet<>();
            Set<String> sinkFormats = new LinkedHashSet<>();
            pcmFormats.add("16-bit");

            if (outputs != null) {
                for (AudioDeviceInfo dev : outputs) {
                    int[] counts = dev.getChannelCounts();
                    if (counts != null) {
                        for (int c : counts) {
                            if (c > maxPcmChannels) {
                                maxPcmChannels = c;
                            }
                        }
                    }
                    int[] encodings = dev.getEncodings();
                    if (encodings != null) {
                        for (int enc : encodings) {
                            if (enc == AudioFormat.ENCODING_PCM_FLOAT) {
                                pcmFormats.add("Float 32-bit");
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    enc == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
                                pcmFormats.add("24-bit");
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                    enc == AudioFormat.ENCODING_PCM_32BIT) {
                                pcmFormats.add("32-bit");
                            } else if (enc == AudioFormat.ENCODING_AC3) {
                                sinkFormats.add("Dolby Digital");
                            } else if (enc == AudioFormat.ENCODING_E_AC3) {
                                sinkFormats.add("DD+");
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                                    enc == AudioFormat.ENCODING_E_AC3_JOC) {
                                sinkFormats.add("Atmos (E-AC-3 JOC)");
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                                    enc == AudioFormat.ENCODING_AC4) {
                                sinkFormats.add("Dolby AC-4");
                            } else if (enc == AudioFormat.ENCODING_DTS) {
                                sinkFormats.add("DTS");
                            } else if (enc == AudioFormat.ENCODING_DTS_HD) {
                                sinkFormats.add("DTS-HD");
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 &&
                                    enc == AudioFormat.ENCODING_DOLBY_TRUEHD) {
                                sinkFormats.add("TrueHD");
                            }
                        }
                    }
                }
            }

            String channelLabel = maxPcmChannels >= 8 ? "7.1" : (maxPcmChannels >= 6 ? "5.1" : "2.0 Stereo");
            StringBuilder sb = new StringBuilder();
            sb.append("PCM: ").append(channelLabel).append(" (").append(TextUtils.join(", ", pcmFormats)).append(")");
            if (!sinkFormats.isEmpty()) {
                sb.append(" · HDMI: ").append(TextUtils.join(", ", sinkFormats));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Spatializer spatializer = audioManager.getSpatializer();
                if (spatializer != null && spatializer.isAvailable()) {
                    sb.append(spatializer.isEnabled() ? " · Spatializer ON" : " · Spatializer pronto");
                }
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "PCM 2.0 Stereo";
        }
    }

    private void applyLowLatencyBufferSizing(AudioTrack audioTrack, int packetFrames, boolean lowLatency) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || audioTrack == null || !lowLatency) {
            return;
        }
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int halFramesPerBurst = parsePositiveInt(
                    audioManager != null ? audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER) : null);
            boolean bluetooth = isBluetoothOutput(audioTrack, audioManager);
            int capacityFrames = audioTrack.getBufferCapacityInFrames();
            int targetFrames = computeLowLatencyBufferFrames(
                    halFramesPerBurst, packetFrames, capacityFrames, true, bluetooth);
            if (targetFrames > 0) {
                int appliedFrames = audioTrack.setBufferSizeInFrames(targetFrames);
                LimeLog.info("AudioTrack FastMixer buffer trimmed: target=" + targetFrames +
                        " applied=" + appliedFrames + " capacity=" + capacityFrames +
                        " halBurst=" + halFramesPerBurst);
            } else {
                LimeLog.info("AudioTrack buffer kept at full capacity (bluetooth=" + bluetooth +
                        ", halBurst=" + halFramesPerBurst + ")");
            }
        } catch (Throwable t) {
            LimeLog.warning("AudioTrack buffer sizing failed: " + t.getMessage());
        }
    }

    @Override
    public int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame) {
        int channelConfig;
        int bytesPerFrame;

        switch (audioConfiguration.channelCount)
        {
            case 2:
                channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            case 4:
                channelConfig = AudioFormat.CHANNEL_OUT_QUAD;
                break;
            case 6:
                channelConfig = AudioFormat.CHANNEL_OUT_5POINT1;
                break;
            case 8:
                // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND isn't available until Android 6.0,
                // yet the CHANNEL_OUT_SIDE_LEFT and CHANNEL_OUT_SIDE_RIGHT constants were added
                // in 5.0, so just hardcode the constant so we can work on Lollipop.
                channelConfig = 0x000018fc; // AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                break;
            default:
                LimeLog.severe("Decoder returned unhandled channel count");
                return -1;
        }

        LimeLog.info("Audio channel config: "+String.format("0x%X", channelConfig));

        floatPcmActive = false;
        int[] encodingsToTry = preferFloatPcm
                ? new int[] { AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_16BIT }
                : new int[] { AudioFormat.ENCODING_PCM_16BIT };

        for (int encoding : encodingsToTry) {
            int sampleBytes = (encoding == AudioFormat.ENCODING_PCM_FLOAT) ? 4 : 2;
            bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * sampleBytes;

            // We'll try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode
            for (int i = 0; i < 4; i++) {
                boolean lowLatency = (i < 2);
                int bufferSize;

                if (i == 0 || i == 2) {
                    bufferSize = bytesPerFrame * 2;
                } else {
                    int minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding);
                    if (minBuf <= 0) {
                        minBuf = bytesPerFrame * 2;
                    }
                    bufferSize = Math.max(minBuf, bytesPerFrame * 2);
                    bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);
                }

                if (lowLatency && !shouldAttemptLowLatencyAudio(
                        Build.VERSION.SDK_INT,
                        AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC),
                        sampleRate,
                        enableAudioFx)) {
                    continue;
                }

                try {
                    track = createAudioTrack(encoding, channelConfig, sampleRate, bufferSize, lowLatency);
                    track.play();
                    floatPcmActive = (encoding == AudioFormat.ENCODING_PCM_FLOAT);
                    applyLowLatencyBufferSizing(track, samplesPerFrame, lowLatency);

                    // Successfully created working AudioTrack. We're done here.
                    LimeLog.info("Audio track configuration: bytes=" + bufferSize + " lowLatency=" + lowLatency +
                            " float32=" + floatPcmActive);
                    break;
                } catch (Exception e) {
                    // Try to release the AudioTrack if we got far enough
                    e.printStackTrace();
                    try {
                        if (track != null) {
                            track.release();
                            track = null;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (track != null) {
                break;
            }
        }

        if (track == null) {
            // Couldn't create any audio track for playback
            return -2;
        }

        if (haptics != null) {
            haptics.setup(sampleRate, audioConfiguration.channelCount);
        }

        return 0;
    }

    private void ensureAudioThreadPriority() {
        if (!audioThreadPriorityElevated) {
            audioThreadPriorityElevated = true;
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean isFloatPcmActive() {
        return floatPcmActive;
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
        ensureAudioThreadPriority();
        if (haptics != null) {
            haptics.process(audioData);
        }

        // Only queue up to the configured cap of pending audio data in addition to what AudioTrack buffers.
        if (MoonBridge.getPendingAudioDuration() < maxPendingAudioMs) {
            // This will block until the write is completed. That can cause a backlog
            // of pending audio data, so we do the above check to bound latency.
            track.write(audioData, 0, audioData.length);
        }
        else {
            LimeLog.info("Too much pending audio data: " + MoonBridge.getPendingAudioDuration() +" ms");
        }
    }

    @Override
    public void playDecodedAudioFloat(float[] audioData) {
        ensureAudioThreadPriority();
        if (haptics != null) {
            haptics.process(audioData);
        }

        if (MoonBridge.getPendingAudioDuration() < maxPendingAudioMs) {
            track.write(audioData, 0, audioData.length, AudioTrack.WRITE_BLOCKING);
        }
        else {
            LimeLog.info("Too much pending audio data: " + MoonBridge.getPendingAudioDuration() + " ms");
        }
    }

    @Override
    public void start() {
        audioThreadPriorityElevated = false;
        if (haptics != null) {
            haptics.start();
        }

        if (enableAudioFx) {
            // Open an audio effect control session to allow equalizers to apply audio effects
            Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, track.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            i.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_GAME);
            context.sendBroadcast(i);
        }
    }

    @Override
    public void stop() {
        if (haptics != null) {
            haptics.stop();
        }

        if (enableAudioFx) {
            // Close our audio effect control session when we're stopping
            Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
            i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, track.getAudioSessionId());
            i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
            context.sendBroadcast(i);
        }
    }

    @Override
    public void cleanup() {
        if (haptics != null) {
            haptics.stop();
        }

        // Immediately drop all pending data
        track.pause();
        track.flush();

        track.release();
    }
}
