package com.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.drift.core.DriftEvent;
import com.drift.ui.state.InterfaceState;
import com.drift.ui.state.StateController;
import com.drift.ui.state.UiModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StateControllerTest {
    final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
    final List<UiModel> seen = new ArrayList<>();
    final StateController c = new StateController(timer, m -> { synchronized (seen) { seen.add(m); } });

    @AfterEach void tearDown() { timer.shutdownNow(); }

    @Test void startsIdleAndOnlyEventsMoveIt() {
        assertThat(c.model().state()).isEqualTo(InterfaceState.IDLE);
        c.onEvent(new DriftEvent.SettingsChanged());
        c.onEvent(new DriftEvent.AudioLevel(0.9)); // no mic: ignored
        assertThat(c.model().state()).isEqualTo(InterfaceState.IDLE);
        assertThat(c.model().level()).isZero();
        assertThat(seen).isEmpty();
    }

    @Test void fullVoiceCommandLifecycle() {
        c.onEvent(new DriftEvent.ListeningStarted("COMMAND"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.LISTENING);
        assertThat(c.model().micActive()).isTrue();
        c.onEvent(new DriftEvent.AudioLevel(0.4));
        assertThat(c.model().level()).isEqualTo(0.4);
        c.onEvent(new DriftEvent.PartialTranscript("open note"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.TRANSCRIBING);
        assertThat(c.model().detail()).isEqualTo("open note");
        c.onEvent(new DriftEvent.ListeningStopped());
        assertThat(c.model().micActive()).isFalse();
        assertThat(c.model().level()).isZero();
        c.onEvent(new DriftEvent.FinalTranscript("open notepad", 0.9));
        assertThat(c.model().detail()).isEqualTo("open notepad");
        c.onEvent(new DriftEvent.CommandPlanned("open notepad", "Open Notepad"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.PROCESSING);
        c.onEvent(new DriftEvent.ExecutionStarted("app.open", "Open Notepad"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.EXECUTING);
        c.onEvent(new DriftEvent.ExecutionFinished("app.open", true, "Open Notepad"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.NOTIFICATION);
        assertThat(c.model().ok()).isTrue();
    }

    @Test void notificationAutoDismissesToIdleAndToMusicWhenPlaying() throws Exception {
        c.onEvent(new DriftEvent.MediaChanged("Song", "Band", "PLAYING"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.MUSIC);
        c.onEvent(new DriftEvent.Notification("Hi", "there"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.NOTIFICATION);
        long end = System.currentTimeMillis() + 8000;
        while (c.model().state() == InterfaceState.NOTIFICATION && System.currentTimeMillis() < end) TimeUnit.MILLISECONDS.sleep(50);
        assertThat(c.model().state()).isEqualTo(InterfaceState.MUSIC);
        c.onEvent(new DriftEvent.MediaChanged(null, null, "NONE"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.IDLE);
    }

    @Test void errorsPersistUntilDismissedAndShowRecovery() {
        c.onEvent(new DriftEvent.Failure("Microphone unavailable", "denied", "Choose another microphone"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.ERROR);
        assertThat(c.model().recovery()).isEqualTo("Choose another microphone");
        c.onEvent(new DriftEvent.Notification("noise", "ignored while error is showing"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.ERROR);
        c.dismiss();
        assertThat(c.model().state()).isEqualTo(InterfaceState.IDLE);
        assertThat(c.model().recovery()).isNull();
    }

    @Test void confirmationAndChoiceCarryPendingIds() {
        c.onEvent(new DriftEvent.ConfirmationRequested("abc", "Run `mvn clean`?"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.PROCESSING);
        assertThat(c.model().pendingId()).isEqualTo("abc");
        assertThat(c.model().pendingPrompt()).contains("mvn clean");
        c.onEvent(new DriftEvent.ChoiceRequested("def", "Which app?", List.of("A", "B")));
        assertThat(c.model().options()).containsExactly("A", "B");
        c.onEvent(new DriftEvent.ExecutionStarted("x", "x"));
        assertThat(c.model().pendingId()).isNull();
    }

    @Test void progressLinesUpdateExecutingDetailWithoutChangingState() {
        c.onEvent(new DriftEvent.ExecutionStarted("dev.run", "Maven tests"));
        c.onEvent(new DriftEvent.Notification("dev.run", "[INFO] Running tests"));
        assertThat(c.model().state()).isEqualTo(InterfaceState.EXECUTING);
        assertThat(c.model().detail()).isEqualTo("[INFO] Running tests");
    }

    @Test void assistantAndDictationPreview() {
        c.onEvent(new DriftEvent.AssistantOpened());
        assertThat(c.model().state()).isEqualTo(InterfaceState.ASSISTANT);
        c.onEvent(new DriftEvent.DictationChanged("PREVIEW", "Notepad", "Hello"));
        assertThat(c.model().dictationText()).isEqualTo("Hello");
        c.onEvent(new DriftEvent.DictationChanged("IDLE", "Notepad", ""));
        assertThat(c.model().state()).isEqualTo(InterfaceState.ASSISTANT); // assistant still open
        c.dismiss();
        assertThat(c.model().state()).isEqualTo(InterfaceState.IDLE);
    }

    @Test void unsupportedReplyIsShownAsNotOk() {
        c.onEvent(new DriftEvent.Reply("I won't guess", false));
        assertThat(c.model().state()).isEqualTo(InterfaceState.NOTIFICATION);
        assertThat(c.model().ok()).isFalse();
    }
}
