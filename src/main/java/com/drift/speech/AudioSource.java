package com.drift.speech;

import java.io.IOException;
import java.util.List;

/** Microphone abstraction so the pipeline is testable without hardware. Format: 16 kHz mono s16le. */
public interface AudioSource extends AutoCloseable {
    /** Blocking read; returns bytes read, or -1 if the device ended/failed. */
    int read(byte[] buffer) throws IOException;
    @Override void close();

    interface Factory {
        List<String> devices();
        AudioSource open(String deviceName) throws IOException;
    }
}
