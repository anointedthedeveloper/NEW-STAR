package com.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.config.SettingsService;
import com.drift.persistence.Database;
import com.drift.speech.AudioSource;
import com.drift.speech.SpeechProvider;
import com.drift.speech.VoiceService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the capture/recognition pipeline with a fake microphone and fake engine (test-only classes). */
class VoiceServiceTest {
    @TempDir Path dir;
    DriftEventBus bus = new DriftEventBus();
    List<DriftEvent> events = new CopyOnWriteArrayList<>();
    SettingsService settings;

    static class FakeProvider implements SpeechProvider {
        SpeechProvider.Status status = new Status(State.READY, "fake");
        final AtomicInteger chunks = new AtomicInteger();
        public String id() { return "fake"; }
        public String displayName() { return "fake"; }
        public Status status(String m, String l) { return status; }
        public Session open(String m, String l) {
            return new Session() {
                public String accept(byte[] pcm, int len) { return "chunk" + chunks.incrementAndGet(); }
                public Transcript finish() { return new Transcript("open notepad", 0.9); }
                public void close() { }
            };
        }
    }

    @BeforeEach void setUp() {
        settings = new SettingsService(new Database(dir.resolve("v.db")), bus);
        bus.subscribe(events::add);
    }

    AudioSource.Factory factory(AtomicInteger opens, boolean failFirstRead, boolean neverRecovers) {
        return new AudioSource.Factory() {
            public List<String> devices() { return List.of("Fake mic"); }
            public AudioSource open(String name) throws IOException {
                int n = opens.incrementAndGet();
                if (n > 1 && neverRecovers) throw new IOException("gone");
                boolean fail = failFirstRead && n == 1;
                return new AudioSource() {
                    volatile boolean closed;
                    public int read(byte[] b) throws IOException {
                        if (closed) return -1;
                        if (fail) return -1;
                        try { Thread.sleep(5); } catch (InterruptedException e) { return -1; }
                        java.util.Arrays.fill(b, (byte) 3);
                        return b.length;
                    }
                    public void close() { closed = true; }
                };
            }
        };
    }

    @Test void pushToTalkProducesFinalTranscriptAndReleasesMic() throws Exception {
        var sinkLatch = new CountDownLatch(1);
        var got = new CopyOnWriteArrayList<String>();
        VoiceService v = new VoiceService(new FakeProvider(), factory(new AtomicInteger(), false, false), settings, bus);
        v.setSink((m, t) -> { got.add(m + ":" + t.text()); sinkLatch.countDown(); });
        assertThat(v.start(VoiceService.Mode.COMMAND)).isTrue();
        assertThat(v.start(VoiceService.Mode.COMMAND)).isFalse(); // single session
        Thread.sleep(100);
        v.stop();
        assertThat(sinkLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(got).containsExactly("COMMAND:open notepad");
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.ListeningStarted);
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.AudioLevel);
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.PartialTranscript);
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.ListeningStopped);
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.FinalTranscript f && f.confidence() == 0.9);
        Thread.sleep(100);
        assertThat(v.isListening()).isFalse();
    }

    @Test void missingModelProducesFailureEventAndNeverOpensMicrophone() {
        FakeProvider p = new FakeProvider();
        p.status = new SpeechProvider.Status(SpeechProvider.State.MODEL_MISSING, "no model");
        AtomicInteger opens = new AtomicInteger();
        VoiceService v = new VoiceService(p, factory(opens, false, false), settings, bus);
        assertThat(v.start(VoiceService.Mode.COMMAND)).isFalse();
        assertThat(opens).hasValue(0);
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.Failure f && f.title().contains("unavailable"));
        assertThat(v.isListening()).isFalse();
    }

    @Test void microphoneOpenFailureIsReportedHonestly() {
        AudioSource.Factory broken = new AudioSource.Factory() {
            public List<String> devices() { return List.of(); }
            public AudioSource open(String n) throws IOException { throw new IOException("denied"); }
        };
        VoiceService v = new VoiceService(new FakeProvider(), broken, settings, bus);
        assertThat(v.start(VoiceService.Mode.COMMAND)).isFalse();
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.Failure f && f.title().equals("Microphone unavailable"));
    }

    @Test void deviceFailureRecoversByReopening() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        VoiceService v = new VoiceService(new FakeProvider(), factory(opens, true, false), settings, bus);
        v.start(VoiceService.Mode.COMMAND);
        Thread.sleep(1200);
        assertThat(opens.get()).isGreaterThanOrEqualTo(2);
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.Notification n && n.message().contains("Reconnected"));
        v.stop();
    }

    @Test void permanentDeviceLossStopsListeningWithFailure() throws Exception {
        VoiceService v = new VoiceService(new FakeProvider(), factory(new AtomicInteger(), true, true), settings, bus);
        v.start(VoiceService.Mode.COMMAND);
        long end = System.currentTimeMillis() + 10_000;
        while (v.isListening() && System.currentTimeMillis() < end) Thread.sleep(50);
        assertThat(v.isListening()).isFalse();
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.Failure f && f.title().equals("Microphone disconnected"));
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.ListeningStopped);
    }

    @Test void safetyTimeLimitStopsListeningAutomatically() throws Exception {
        VoiceService v = new VoiceService(new FakeProvider(), factory(new AtomicInteger(), false, false), settings, bus);
        v.setLimits(200, 200);
        v.start(VoiceService.Mode.COMMAND);
        long end = System.currentTimeMillis() + 5_000;
        while (v.isListening() && System.currentTimeMillis() < end) Thread.sleep(25);
        assertThat(v.isListening()).isFalse();
        assertThat(events).anyMatch(e -> e instanceof DriftEvent.Notification n && n.message().contains("limit"));
    }

    @Test void rmsLevelIsBounded() {
        byte[] silent = new byte[3200];
        byte[] loud = new byte[3200];
        for (int i = 0; i < loud.length; i += 2) { loud[i] = (byte) 0xFF; loud[i + 1] = 0x7F; }
        assertThat(VoiceService.rms(silent, silent.length)).isZero();
        assertThat(VoiceService.rms(loud, loud.length)).isEqualTo(1.0);
    }
}
