package com.limelight.binding.audio;

interface PcmWriter<T> {
    int write(T samples, int offset, int count);

    default int writeAll(T samples, int length) {
        int offset = 0;
        while (offset < length) {
            int written = write(samples, offset, length - offset);
            if (written <= 0) {
                return written;
            }
            offset += written;
        }
        return offset;
    }
}
