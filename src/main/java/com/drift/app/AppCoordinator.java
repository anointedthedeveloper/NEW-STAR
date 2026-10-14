package com.drift.app;

import com.drift.actions.CommandOrchestrator;
import com.drift.actions.CommandOutcome;
import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.dictation.DictationService;
import com.drift.speech.VoiceService;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

/** Connects speech output to the command pipeline / dictation, and turns outcomes into user-facing replies. */
@Component
public class AppCoordinator {
    private final VoiceService voice;
    private final CommandOrchestrator orchestrator;
    private final DictationService dictation;
    private final DriftEventBus bus;
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    public AppCoordinator(VoiceService voice, CommandOrchestrator orchestrator, DictationService dictation, DriftEventBus bus) {
        this.voice = voice;
        this.orchestrator = orchestrator;
        this.dictation = dictation;
        this.bus = bus;
    }

    @PostConstruct
    void wire() {
        voice.setSink((mode, t) -> {
            if (mode == VoiceService.Mode.DICTATION) {
                dictation.append(t.text());
                dictation.stop();
            } else {
                submit(t.text());
            }
        });
    }

    /** Runs a typed or spoken command off the calling thread and publishes the reply. */
    public void submit(String utterance) {
        worker.submit(() -> reply(orchestrator.process(utterance)));
    }

    public void confirm(String pendingId, boolean approve) { worker.submit(() -> reply(orchestrator.confirm(pendingId, approve))); }

    public void choose(String pendingId, int index, boolean remember) { worker.submit(() -> reply(orchestrator.choose(pendingId, index, remember))); }

    private void reply(CommandOutcome o) {
        switch (o) {
            case CommandOutcome.Done d -> bus.publish(new DriftEvent.Reply(d.message(), true));
            case CommandOutcome.NeedsConfirmation c -> { /* ConfirmationRequested already published */ }
            case CommandOutcome.NeedsChoice c -> { /* ChoiceRequested already published */ }
            case CommandOutcome.Refused r -> bus.publish(new DriftEvent.Reply(r.message(), false));
            case CommandOutcome.Unsupported u -> bus.publish(new DriftEvent.Reply(u.message(), false));
            case CommandOutcome.Failed f -> bus.publish(new DriftEvent.Failure("That didn't work", f.message(), "Check the details or try again"));
        }
    }
}
