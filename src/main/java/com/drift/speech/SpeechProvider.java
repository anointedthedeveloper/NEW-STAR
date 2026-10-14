package com.drift.speech;

/** A replaceable offline speech-recognition engine. */
public interface SpeechProvider {
    enum State { READY, MODEL_MISSING, LIBRARY_UNAVAILABLE, ERROR }

    record Status(State state, String detail) {
        public boolean ready() { return state == State.READY; }
    }

    record Transcript(String text, double confidence) {
        public static final double UNKNOWN_CONFIDENCE = -1;
    }

    interface Session extends AutoCloseable {
        /** Feeds 16 kHz mono signed 16-bit little-endian PCM. Returns partial text for display, or null. */
        String accept(byte[] pcm, int length);
        /** Ends the utterance and returns the final text (may be empty). */
        Transcript finish();
        @Override void close();
    }

    String id();
    String displayName();
    /** Cheap availability check (no recognition). Never throws. */
    Status status(String modelPath, String language);
    Session open(String modelPath, String language) throws SpeechException;

    class SpeechException extends Exception {
        public SpeechException(String m) { super(m); }
        public SpeechException(String m, Throwable c) { super(m, c); }
    }
}
