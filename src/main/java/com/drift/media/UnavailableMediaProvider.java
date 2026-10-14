package com.drift.media;

import java.util.List;
import java.util.Optional;

/** Honest stand-in on platforms (or failures) where media sessions cannot be read. Never returns fake data. */
public class UnavailableMediaProvider implements MediaProvider {
    private final String reason;
    public UnavailableMediaProvider(String reason) { this.reason = reason; }
    @Override public Availability availability() { return new Availability(false, reason); }
    @Override public List<MediaSession> sessions() { return List.of(); }
    @Override public Optional<String> currentSessionId() { return Optional.empty(); }
    @Override public void send(String id, Command c) throws MediaException { throw new MediaException(reason); }
    @Override public Optional<byte[]> artwork(String id) { return Optional.empty(); }
}
