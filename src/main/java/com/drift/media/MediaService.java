package com.drift.media;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Media session state for the UI and voice commands. Polls only while something is observing
 * (the floating UI / a media widget calls {@link #observe()}); there is no background polling otherwise.
 */
@Service
public class MediaService {
    private final MediaProvider provider;
    private final DriftEventBus bus;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("drift-media-poll").unstarted(r));
    private final AtomicInteger observers = new AtomicInteger();
    private ScheduledFuture<?> poller;
    private volatile List<MediaSession> sessions = List.of();
    private volatile String selectedId;
    private volatile String lastPublished = "";

    public MediaService(MediaProvider provider, DriftEventBus bus) {
        this.provider = provider;
        this.bus = bus;
    }

    public MediaProvider.Availability availability() { return provider.availability(); }

    public synchronized Runnable observe() {
        if (observers.incrementAndGet() == 1) poller = scheduler.scheduleWithFixedDelay(this::refreshQuietly, 0, 2, TimeUnit.SECONDS);
        java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
        return () -> { if (released.compareAndSet(false, true)) release(); };
    }

    private synchronized void release() {
        if (observers.decrementAndGet() == 0 && poller != null) { poller.cancel(false); poller = null; }
    }

    private void refreshQuietly() {
        try { refresh(); } catch (MediaProvider.MediaException ignored) { /* surfaced through availability() */ }
    }

    public List<MediaSession> refresh() throws MediaProvider.MediaException {
        List<MediaSession> list = provider.sessions();
        sessions = list;
        Optional<MediaSession> cur = current();
        String sig = cur.map(s -> s.id() + "|" + s.title() + "|" + s.status()).orElse("");
        if (!sig.equals(lastPublished)) {
            lastPublished = sig;
            bus.publish(cur.map(s -> new DriftEvent.MediaChanged(s.title(), s.artist(), s.status().name()))
                    .orElse(new DriftEvent.MediaChanged(null, null, "NONE")));
        }
        return list;
    }

    public List<MediaSession> sessions() { return sessions; }

    public void select(String sessionId) { selectedId = sessionId; }

    /** The user-selected session if still present, else the OS-current one, else the first playing, else the first. */
    public Optional<MediaSession> current() {
        List<MediaSession> list = sessions;
        if (list.isEmpty()) return Optional.empty();
        String sel = selectedId;
        if (sel != null) { Optional<MediaSession> s = list.stream().filter(x -> x.id().equals(sel)).findFirst(); if (s.isPresent()) return s; }
        Optional<String> os = provider.currentSessionId();
        if (os.isPresent()) { Optional<MediaSession> s = list.stream().filter(x -> x.id().equals(os.get())).findFirst(); if (s.isPresent()) return s; }
        return list.stream().filter(x -> x.status() == MediaSession.Status.PLAYING).findFirst().or(() -> Optional.of(list.get(0)));
    }

    /** Honest description for "what song is playing". */
    public String describeNowPlaying() {
        var av = provider.availability();
        if (!av.available()) return "I can't read media sessions here: " + av.detail();
        try { refresh(); } catch (MediaProvider.MediaException e) { return "I couldn't read the media session: " + e.getMessage(); }
        return current().map(s -> (s.status() == MediaSession.Status.PLAYING ? "Playing: " : "Not playing (" + s.status().name().toLowerCase() + "): ") + s.display()
                + (s.album() != null ? " (" + s.album() + ")" : "")).orElse("Nothing is playing - no media session is active.");
    }

    /** Sends a control to the current session, only if that session reports the capability. */
    public String control(MediaProvider.Command command) throws MediaProvider.MediaException {
        var av = provider.availability();
        if (!av.available()) throw new MediaProvider.MediaException("Media control is unavailable: " + av.detail());
        refresh();
        MediaSession s = current().orElseThrow(() -> new MediaProvider.MediaException("No media session is active."));
        boolean supported = switch (command) {
            case NEXT -> s.canNext();
            case PREVIOUS -> s.canPrevious();
            default -> s.canPlayPause();
        };
        if (!supported) throw new MediaProvider.MediaException(s.app() + " does not allow that control right now.");
        provider.send(s.id(), command);
        return command.name().toLowerCase() + " sent to " + s.app();
    }

    public Optional<byte[]> artwork() {
        try {
            Optional<MediaSession> s = current();
            return s.isPresent() && s.get().hasArtwork() ? provider.artwork(s.get().id()) : Optional.empty();
        } catch (MediaProvider.MediaException e) { return Optional.empty(); }
    }

    @PreDestroy void shutdown() { scheduler.shutdownNow(); provider.close(); }
}
