package com.limelight.binding.audio;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Process;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.jni.MoonBridge;

public class AndroidAudioRenderer implements AudioRenderer {

    public static final int DEFAULT_MAX_PENDING_AUDIO_MS = 30;

    private final Context context;
    private final boolean enableAudioFx;
    private final int maxPendingAudioMs;

    private AudioTrack track;
    private boolean audioThreadPriorityElevated;

    // Optional: turns the bass in the audio into vibration
    private final AudioHaptics haptics;

    public static int clampMaxPendingAudioMs(int ms) {
        return Math.max(10, Math.min(100, ms));
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx) {
        this(context, enableAudioFx, null, DEFAULT_MAX_PENDING_AUDIO_MS);
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx, AudioHaptics haptics) {
        this(context, enableAudioFx, haptics, DEFAULT_MAX_PENDING_AUDIO_MS);
    }

    public AndroidAudioRenderer(Context context, boolean enableAudioFx, AudioHaptics haptics, int maxPendingAudioMs) {
        this.context = context;
        this.enableAudioFx = enableAudioFx;
        this.haptics = haptics;
        this.maxPendingAudioMs = clampMaxPendingAudioMs(maxPendingAudioMs);
    }

    private AudioTrack createAudioTrack(int channelConfig, int sampleRate, int bufferSize, boolean lowLatency) {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME);
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
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

    @TargetApi(Build.VERSION_CODES.S)
    public static boolean isBluetoothAudioDeviceType(int sdkInt, int deviceType) {
        if (sdkInt < Build.VERSION_CODES.M) {
            return false;
        }
        if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (sdkInt >= Build.VERSION_CODES.P && deviceType == AudioDeviceInfo.TYPE_HEARING_AID) {
            return true;
        }
        return sdkInt >= Build.VERSION_CODES.S &&
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

        bytesPerFrame = audioConfiguration.channelCount * samplesPerFrame * 2;

        // We're not supposed to request less than the minimum
        // buffer size for our buffer, but it appears that we can
        // do this on many devices and it lowers audio latency.
        // We'll try the small buffer size first and if it fails,
        // use the recommended larger buffer size.

        for (int i = 0; i < 4; i++) {
            boolean lowLatency;
            int bufferSize;

            // We will try:
            // 1) Small buffer, low latency mode
            // 2) Large buffer, low latency mode
            // 3) Small buffer, standard mode
            // 4) Large buffer, standard mode

            switch (i) {
                case 0:
                case 1:
                    lowLatency = true;
                    break;
                case 2:
                case 3:
                    lowLatency = false;
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            switch (i) {
                case 0:
                case 2:
                    bufferSize = bytesPerFrame * 2;
                    break;

                case 1:
                case 3:
                    // Try the larger buffer size
                    bufferSize = Math.max(AudioTrack.getMinBufferSize(sampleRate,
                            channelConfig,
                            AudioFormat.ENCODING_PCM_16BIT),
                            bytesPerFrame * 2);

                    // Round to next frame
                    bufferSize = (((bufferSize + (bytesPerFrame - 1)) / bytesPerFrame) * bytesPerFrame);
                    break;
                default:
                    // Unreachable
                    throw new IllegalStateException();
            }

            if (lowLatency && !shouldAttemptLowLatencyAudio(
                    Build.VERSION.SDK_INT,
                    AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC),
                    sampleRate,
                    enableAudioFx)) {
                continue;
            }

            try {
                track = createAudioTrack(channelConfig, sampleRate, bufferSize, lowLatency);
                track.play();
                applyLowLatencyBufferSizing(track, samplesPerFrame, lowLatency);

                // Successfully created working AudioTrack. We're done here.
                LimeLog.info("Audio track configuration: "+bufferSize+" "+lowLatency);
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

        if (track == null) {
            // Couldn't create any audio track for playback
            return -2;
        }

        if (haptics != null) {
            haptics.setup(sampleRate, audioConfiguration.channelCount);
        }

        return 0;
    }

    @Override
    public void playDecodedAudio(short[] audioData) {
        if (!audioThreadPriorityElevated) {
            audioThreadPriorityElevated = true;
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            } catch (Throwable ignored) {
            }
        }
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
