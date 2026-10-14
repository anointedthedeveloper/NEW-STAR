package com.drift.media;

import java.util.List;
import java.util.Optional;

public interface MediaProvider {
    enum Command { PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS }

    record Availability(boolean available, String detail) {}

    Availability availability();
    /** Current sessions; empty list when nothing is playing. Throws only for provider failures. */
    List<MediaSession> sessions() throws MediaException;
    /** Returns the session the OS considers current, if any. */
    Optional<String> currentSessionId();
    void send(String sessionId, Command command) throws MediaException;
    /** PNG/JPEG bytes of the artwork, if available. */
    Optional<byte[]> artwork(String sessionId) throws MediaException;
    default void close() { }

    class MediaException extends Exception {
        public MediaException(String m) { super(m); }
        public MediaException(String m, Throwable c) { super(m, c); }
    }
}
