package com.limelight.binding.video;

/**
 * Computes conservative minimum MediaCodec input buffer capacities and safe runtime expansions
 * for high-bitrate HEVC/AVC/AV1 keyframes so Amlogic/Codec2 decoders do not fail or crash on
 * oversized IDR units.
 */
public final class DecoderInputBufferSizing {
    public static final int MIN_AVC_AV1_INPUT_BYTES = 1024 * 1024;
    public static final int MIN_HEVC_INPUT_BYTES = 2 * 1024 * 1024;
    public static final int MAX_INPUT_BYTES = 16 * 1024 * 1024;
    private static final int ALIGN_BYTES = 64 * 1024;

    private DecoderInputBufferSizing() {}

    public static int initialInputBufferSize(String mimeType, int width, int height, int bitrateKbps) {
        int baseFloor = "video/hevc".equalsIgnoreCase(mimeType)
                ? MIN_HEVC_INPUT_BYTES
                : MIN_AVC_AV1_INPUT_BYTES;

        long bitrateFloor = Math.max(0L, (long) bitrateKbps) * 1000L / 8L / 2L;
        long pixelFloor = Math.max(0L, (long) width) * Math.max(0L, (long) height) * 3L / 4L;
        long target = Math.max(baseFloor, Math.max(bitrateFloor, pixelFloor));
        long aligned = ((target + ALIGN_BYTES - 1L) / ALIGN_BYTES) * ALIGN_BYTES;
        return (int) Math.min(MAX_INPUT_BYTES, Math.max(baseFloor, aligned));
    }

    public static int expandedInputBufferSize(int currentLimitBytes, int requiredBytes) {
        if (requiredBytes <= 0 || requiredBytes > MAX_INPUT_BYTES) {
            return -1;
        }
        long alignedRequired = ((long) requiredBytes + ALIGN_BYTES - 1L) / ALIGN_BYTES * ALIGN_BYTES;
        long doubled = (long) Math.max(MIN_AVC_AV1_INPUT_BYTES, currentLimitBytes) * 2L;
        long next = Math.max(doubled, alignedRequired);
        if (next <= currentLimitBytes) {
            return -1;
        }
        return (int) Math.min(MAX_INPUT_BYTES, next);
    }
}
