package com.drift.speech;

import com.drift.config.SettingsService;
import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Explicit, user-triggered speech capture. Nothing is recorded unless {@link #start} was called, audio is only
 * held in a small bounded queue (never written to disk) and is discarded as soon as it has been recognised.
 * Capture and recognition run on their own threads; results are published as events (never on the JavaFX thread).
 */
@Service
public class VoiceService {
    public enum Mode { COMMAND, DICTATION }

    public interface TranscriptSink { void onFinal(Mode mode, SpeechProvider.Transcript transcript); }

    static final int CHUNK_BYTES = 3200;            // 100 ms at 16 kHz mono 16-bit
    static final int QUEUE_CAPACITY = 64;           // ~6.4 s of audio at most buffered
    static final int MAX_REOPEN_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);
    private static final byte[] END = new byte[0];

    private final SpeechProvider provider;
    private final AudioSource.Factory audio;
    private final SettingsService settings;
    private final DriftEventBus bus;
    private volatile TranscriptSink sink = (m, t) -> { };
    private volatile Run active;
    private final AtomicInteger droppedChunks = new AtomicInteger();
    private volatile long maxCommandMillis = 30_000, maxDictationMillis = 10 * 60_000;

    public VoiceService(SpeechProvider provider, AudioSource.Factory audio, SettingsService settings, DriftEventBus bus) {
        this.provider = provider;
        this.audio = audio;
        this.settings = settings;
        this.bus = bus;
    }

    public void setSink(TranscriptSink sink) { this.sink = sink; }

    public boolean isListening() { return active != null; }

    public SpeechProvider.Status providerStatus() {
        var v = settings.get().voice();
        return provider.status(v.modelPath(), v.language());
    }

    public java.util.List<String> microphones() { return audio.devices(); }

    public int droppedChunks() { return droppedChunks.get(); }

    /** Test hook: shorten the safety caps. */
    public void setLimits(long commandMillis, long dictationMillis) { this.maxCommandMillis = commandMillis; this.maxDictationMillis = dictationMillis; }

    public synchronized boolean start(Mode mode) {
        if (active != null) return false;
        var v = settings.get().voice();
        SpeechProvider.Status st = provider.status(v.modelPath(), v.language());
        if (!st.ready()) {
            bus.publish(new DriftEvent.Failure("Speech recognition unavailable", st.detail(),
                    st.state() == SpeechProvider.State.MODEL_MISSING ? "Install a model (docs/SPEECH_SETUP.md), then try again" : "Check Settings > Diagnostics"));
            return false;
        }
        SpeechProvider.Session session;
        AudioSource source;
        try {
            session = provider.open(v.modelPath(), v.language());
        } catch (SpeechProvider.SpeechException e) {
            bus.publish(new DriftEvent.Failure("Speech engine failed to start", e.getMessage(), "Check the model folder in Settings > Voice recognition"));
            return false;
        }
        try {
            source = audio.open(v.microphone());
        } catch (IOException e) {
            session.close();
            bus.publish(new DriftEvent.Failure("Microphone unavailable", e.getMessage(), "Choose another microphone in Settings > Voice recognition"));
            return false;
        }
        Run run = new Run(mode, session, source, v.microphone());
        active = run;
        bus.publish(new DriftEvent.ListeningStarted(mode.name()));
        Thread rec = Thread.ofPlatform().daemon().name("drift-recognizer").unstarted(run::recognizeLoop);
        Thread cap = Thread.ofPlatform().daemon().name("drift-capture").unstarted(run::captureLoop);
        rec.start();
        cap.start();
        return true;
    }

    /** Stops capture immediately (microphone released); recognition of buffered audio completes asynchronously. */
    public synchronized void stop() {
        Run r = active;
        if (r != null) r.requestStop();
    }

    @PreDestroy
    void shutdown() { stop(); }

    private final class Run {
        final Mode mode;
        final SpeechProvider.Session session;
        final String deviceName;
        volatile AudioSource source;
        final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        final AtomicBoolean stopRequested = new AtomicBoolean();
        final AtomicBoolean micReleased = new AtomicBoolean();

        Run(Mode mode, SpeechProvider.Session session, AudioSource source, String deviceName) {
            this.mode = mode;
            this.session = session;
            this.source = source;
            this.deviceName = deviceName;
        }

        void requestStop() {
            if (stopRequested.compareAndSet(false, true)) releaseMic();
        }

        void releaseMic() {
            if (micReleased.compareAndSet(false, true)) {
                AudioSource s = source;
                if (s != null) s.close();
                bus.publish(new DriftEvent.ListeningStopped());
            }
        }

        void captureLoop() {
            long limit = mode == Mode.COMMAND ? maxCommandMillis : maxDictationMillis;
            long deadline = System.currentTimeMillis() + limit;
            int reopenAttempts = 0;
            try {
                while (!stopRequested.get()) {
                    if (System.currentTimeMillis() > deadline) {
                        bus.publish(new DriftEvent.Notification("Listening stopped", "Reached the " + limit / 1000 + " second limit."));
                        break;
                    }
                    byte[] buf = new byte[CHUNK_BYTES];
                    int n;
                    try {
                        n = source.read(buf);
                    } catch (IOException | RuntimeException e) {
                        n = -1;
                    }
                    if (n < 0) {
                        if (stopRequested.get()) break;
                        if (!reopen(++reopenAttempts)) break;
                        continue;
                    }
                    reopenAttempts = 0;
                    if (n == 0) continue;
                    bus.publish(new DriftEvent.AudioLevel(rms(buf, n)));
                    byte[] chunk = n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
                    while (!queue.offer(chunk)) {          // bounded: drop the oldest audio rather than grow
                        if (queue.poll() != null) droppedChunks.incrementAndGet();
                    }
                }
            } finally {
                releaseMic();
                while (!queue.offer(END)) queue.poll();
            }
        }

        private boolean reopen(int attempt) {
            AudioSource old = source;
            if (old != null) old.close();
            if (attempt > MAX_REOPEN_ATTEMPTS) {
                bus.publish(new DriftEvent.Failure("Microphone disconnected", "The audio device stopped delivering audio and could not be reopened.",
                        "Reconnect the microphone, or choose another one in Settings"));
                return false;
            }
            try {
                Thread.sleep(300L * attempt);
                source = audio.open(deviceName);
                bus.publish(new DriftEvent.Notification("Microphone", "Reconnected to the audio device."));
                return true;
            } catch (IOException e) {
                return reopen(attempt + 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        void recognizeLoop() {
            String lastPartial = "";
            try {
                while (true) {
                    byte[] chunk = queue.take();
                    if (chunk == END) break;
                    String partial = session.accept(chunk, chunk.length);
                    if (partial != null && !partial.equals(lastPartial) && !partial.isBlank()) {
                        lastPartial = partial;
                        bus.publish(new DriftEvent.PartialTranscript(partial));
                    }
                }
                SpeechProvider.Transcript t = session.finish();
                if (t.text().isBlank()) {
                    bus.publish(new DriftEvent.Notification("Didn't catch that", "No speech was recognised."));
                } else {
                    bus.publish(new DriftEvent.FinalTranscript(t.text(), t.confidence()));
                    try { sink.onFinal(mode, t); } catch (RuntimeException e) { log.warn("Transcript sink failed", e); }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                log.warn("Recognition failed", e);
                bus.publish(new DriftEvent.Failure("Recognition failed", String.valueOf(e.getMessage()), "Try again; check Settings > Diagnostics"));
            } finally {
                session.close();
                synchronized (VoiceService.this) { if (active == this) active = null; }
            }
        }
    }

    public static double rms(byte[] buf, int n) {
        long sum = 0;
        int samples = n / 2;
        for (int i = 0; i + 1 < n; i += 2) {
            short s = (short) ((buf[i + 1] << 8) | (buf[i] & 0xff));
            sum += (long) s * s;
        }
        return samples == 0 ? 0 : Math.min(1.0, Math.sqrt((double) sum / samples) / 8000.0);
    }
}
