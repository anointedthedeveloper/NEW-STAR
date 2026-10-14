package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.drift.speech.ModelLocator;
import com.drift.speech.SpeechProvider;
import com.drift.speech.VoskSpeechProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real offline recognition. Runs only when DRIFT_TEST_MODELS_DIR (folder containing a Vosk model) and
 * DRIFT_TEST_WAV (16 kHz mono 16-bit WAV) are set, so ordinary builds need no model or audio.
 */
class VoskIntegrationTest {
    @TempDir Path empty;

    @Test void reportsMissingModelHonestly() {
        var p = new VoskSpeechProvider(new ModelLocator(empty));
        var st = p.status("", "en-US");
        assertThat(st.state()).isEqualTo(SpeechProvider.State.MODEL_MISSING);
        assertThat(st.detail()).contains("alphacephei.com/vosk/models");
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(SpeechProvider.SpeechException.class, () -> p.open("", "en-US"))).isNotNull();
    }

    @Test void transcribesRealAudioWithRealModel() throws Exception {
        String models = System.getenv("DRIFT_TEST_MODELS_DIR");
        String wav = System.getenv("DRIFT_TEST_WAV");
        assumeTrue(models != null && wav != null, "DRIFT_TEST_MODELS_DIR / DRIFT_TEST_WAV not set");
        var provider = new VoskSpeechProvider(new ModelLocator(Path.of(models)));
        assertThat(provider.status("", "en-US").ready()).isTrue();
        byte[] all = Files.readAllBytes(Path.of(wav));
        byte[] pcm = Arrays.copyOfRange(all, 44, all.length); // canonical 44-byte WAV header
        try (var session = provider.open("", "en-US")) {
            for (int i = 0; i < pcm.length; i += 3200) session.accept(Arrays.copyOfRange(pcm, i, Math.min(pcm.length, i + 3200)), Math.min(3200, pcm.length - i));
            var t = session.finish();
            System.out.println("VOSK TRANSCRIPT: " + t.text() + " (confidence " + t.confidence() + ")");
            assertThat(t.text()).isNotBlank();
            assertThat(t.confidence()).isBetween(0.0, 1.0);
        }
    }
}
