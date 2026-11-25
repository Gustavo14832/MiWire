package com.limelight.nvstream.av.audio;

import com.limelight.nvstream.jni.MoonBridge;

public interface AudioRenderer {
    int setup(MoonBridge.AudioConfiguration audioConfiguration, int sampleRate, int samplesPerFrame);

    void start();

    void stop();
    
    void playDecodedAudio(short[] audioData);

    default void playDecodedAudioFloat(float[] audioData) {}

    default boolean isFloatPcmActive() {
        return false;
    }

    void cleanup();
}
