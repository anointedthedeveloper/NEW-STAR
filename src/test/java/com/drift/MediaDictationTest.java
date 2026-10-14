package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.dictation.DictationService;
import com.drift.dictation.ForegroundWindow;
import com.drift.dictation.Punctuation;
import com.drift.dictation.TextInserter;
import com.drift.media.BridgeMediaProvider;
import com.drift.media.MediaProvider;
import com.drift.media.MediaService;
import com.drift.media.MediaSession;
import com.drift.media.UnavailableMediaProvider;
import com.drift.windows.PlatformInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaDictationTest {
    @TempDir Path dir;

    // ---- dictation ----
    static class FakeInserter implements TextInserter {
        final List<String> typed = new ArrayList<>();
        int undos;
        boolean supported = true;
        public void insert(String t) { typed.add(t); }
        public void sendUndo() { undos++; }
        public boolean supported() { return supported; }
    }

    static class Fg implements ForegroundWindow {
        volatile long handle = 1;
        public Optional<Info> current() { return Optional.of(new Info(handle, "Editor " + handle)); }
    }

    @Test void punctuationCommandsAndCapitalisation() {
        assertThat(Punctuation.apply("hello comma world period how are you question mark")).isEqualTo("Hello, world. How are you?");
        assertThat(Punctuation.apply("first line new line second line")).isEqualTo("First line\nSecond line");
        assertThat(Punctuation.apply("a new paragraph b")).isEqualTo("A\n\nB");
    }

    @Test void dictationRequiresPreviewCommitAndSameTarget() throws Exception {
        FakeInserter ins = new FakeInserter();
        Fg fg = new Fg();
        DriftEventBus bus = new DriftEventBus();
        List<DriftEvent> ev = new ArrayList<>();
        bus.subscribe(ev::add);
        DictationService d = new DictationService(ins, fg, bus);
        d.start();
        d.append("hello comma world");
        assertThat(d.state()).isEqualTo(DictationService.State.DICTATING);
        assertThat(ins.typed).isEmpty();               // nothing typed while dictating
        d.stop();
        assertThat(d.state()).isEqualTo(DictationService.State.PREVIEW);
        assertThat(ins.typed).isEmpty();               // nothing typed until commit
        fg.handle = 2;                                  // focus moved
        assertThatThrownBy(d::commit).isInstanceOf(DictationService.DictationException.class).hasMessageContaining("nothing was typed");
        assertThat(ins.typed).isEmpty();
        assertThat(d.state()).isEqualTo(DictationService.State.PREVIEW);
        fg.handle = 1;
        d.commit();
        assertThat(ins.typed).containsExactly("Hello, world");
        assertThat(d.state()).isEqualTo(DictationService.State.IDLE);
        d.undoLastInsert();
        assertThat(ins.undos).isEqualTo(1);
        assertThat(ev).anyMatch(e -> e instanceof DriftEvent.DictationChanged c && c.state().equals("PREVIEW") && c.target().equals("Editor 1"));
    }

    @Test void discardAndEmptyDictationNeverType() throws Exception {
        FakeInserter ins = new FakeInserter();
        DictationService d = new DictationService(ins, new Fg(), new DriftEventBus());
        d.start();
        d.stop();
        assertThat(d.state()).isEqualTo(DictationService.State.IDLE);
        d.stage("secret text");
        d.discard();
        assertThatThrownBy(d::commit).isInstanceOf(DictationService.DictationException.class);
        assertThat(ins.typed).isEmpty();
    }

    @Test void dictationRefusedWhereTypingUnsupported() {
        FakeInserter ins = new FakeInserter();
        ins.supported = false;
        DictationService d = new DictationService(ins, new Fg(), new DriftEventBus());
        assertThatThrownBy(d::start).hasMessageContaining("only supported on Windows");
    }

    // ---- media ----
    private BridgeMediaProvider bridge(String script) throws Exception {
        assumeFalse(PlatformInfo.isWindows());
        Path s = dir.resolve("bridge.sh");
        Files.writeString(s, script);
        return new BridgeMediaProvider(List.of("sh", s.toString()));
    }

    private static final String HELPER = """
            echo '{"ready":true}'
            while read -r line; do
              case "$line" in
                LIST) echo '{"sessions":[{"id":"spotify","app":"Spotify","title":"Song","artist":"Band","album":"Alb","status":"PLAYING","position":10,"duration":200,"canPlayPause":true,"canNext":true,"canPrevious":false,"hasArtwork":true}],"current":"spotify"}';;
                CTRL*) echo '{"ok":true}';;
                ART*) echo '{"data":"aGk="}';;
                *) echo '{"error":"unknown"}';;
              esac
            done
            """;

    @Test void bridgeProviderParsesSessionsArtworkAndControls() throws Exception {
        BridgeMediaProvider p = bridge(HELPER);
        MediaService svc = new MediaService(p, new DriftEventBus());
        assertThat(svc.describeNowPlaying()).contains("Playing: Song - Band").contains("Alb");
        MediaSession s = svc.current().orElseThrow();
        assertThat(s.canNext()).isTrue();
        assertThat(svc.control(MediaProvider.Command.NEXT)).contains("next sent to Spotify");
        assertThatThrownBy(() -> svc.control(MediaProvider.Command.PREVIOUS)).hasMessageContaining("does not allow");
        assertThat(svc.artwork()).contains("hi".getBytes());
        p.close();
    }

    @Test void bridgeRestartsAfterHelperCrash() throws Exception {
        Path marker = dir.resolve("marker");
        BridgeMediaProvider p = bridge("echo '{\"ready\":true}'\nif [ ! -f " + marker + " ]; then touch " + marker + "; read -r line; exit 1; fi\n" + HELPER.substring(HELPER.indexOf("while")));
        assertThat(p.sessions()).hasSize(1);
        p.close();
    }

    @Test void emptyStateAndUnavailableProvidersAreHonest() throws Exception {
        MediaService none = new MediaService(new UnavailableMediaProvider("Media sessions need Windows 10 or 11"), new DriftEventBus());
        assertThat(none.describeNowPlaying()).contains("can't read media sessions").contains("Windows 10 or 11");
        assertThatThrownBy(() -> none.control(MediaProvider.Command.PAUSE)).hasMessageContaining("unavailable");
        BridgeMediaProvider p = bridge("echo '{\"ready\":true}'\nwhile read -r l; do echo '{\"sessions\":[],\"current\":null}'; done\n");
        MediaService empty = new MediaService(p, new DriftEventBus());
        assertThat(empty.describeNowPlaying()).contains("Nothing is playing");
        assertThat(empty.current()).isEmpty();
        p.close();
    }

    @Test void brokenHelperReportsUnavailabilityInsteadOfFakeData() throws Exception {
        BridgeMediaProvider p = new BridgeMediaProvider(List.of("sh", "-c", "exit 1"));
        assertThatThrownBy(p::sessions).isInstanceOf(MediaProvider.MediaException.class);
        assertThat(p.availability().available()).isFalse();
        p.close();
    }
}
