package com.drift.ui.state;

import com.drift.core.DriftEvent;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Pure state machine behind the floating interface. It reacts only to {@link DriftEvent}s, so the UI can never
 * claim an operation is happening unless the application reported it. Thread-safe; listeners must hop to the UI thread.
 */
public class StateController {
    static final long NOTIFICATION_MILLIS = 4000, REPLY_MILLIS = 7000;

    private final ScheduledExecutorService timer;
    private final Consumer<UiModel> listener;
    private UiModel model = UiModel.idle();
    private ScheduledFuture<?> dismissTask;
    private boolean assistantOpen;

    public StateController(ScheduledExecutorService timer, Consumer<UiModel> listener) {
        this.timer = timer;
        this.listener = listener;
    }

    public synchronized UiModel model() { return model; }

    public void onEvent(DriftEvent e) {
        UiModel changed;
        synchronized (this) {
            changed = reduce(e);
            if (changed == null || changed.equals(model)) return;
            model = changed;
        }
        listener.accept(changed);
    }

    private UiModel reduce(DriftEvent e) {
        UiModel m = model;
        boolean busy = m.state() == InterfaceState.LISTENING || m.state() == InterfaceState.TRANSCRIBING
                || m.state() == InterfaceState.PROCESSING || m.state() == InterfaceState.EXECUTING;
        switch (e) {
            case DriftEvent.ListeningStarted s -> {
                cancelDismiss();
                return m.withMic(true).with(InterfaceState.LISTENING, s.mode().equals("DICTATION") ? "Dictating" : "Listening", "Speak now").clearedPending();
            }
            case DriftEvent.AudioLevel l -> { return m.micActive() ? m.withLevel(l.level()) : null; }
            case DriftEvent.PartialTranscript p -> {
                if (m.state() == InterfaceState.LISTENING || m.state() == InterfaceState.TRANSCRIBING)
                    return m.with(InterfaceState.TRANSCRIBING, m.micActive() ? "Listening" : "Transcribing", p.text());
                return null;
            }
            case DriftEvent.ListeningStopped s -> {
                UiModel off = m.withMic(false);
                if (m.state() == InterfaceState.LISTENING) return off.with(InterfaceState.TRANSCRIBING, "Transcribing", "Finishing up...");
                if (m.state() == InterfaceState.TRANSCRIBING) return off.with(InterfaceState.TRANSCRIBING, "Transcribing", m.detail());
                return off;
            }
            case DriftEvent.FinalTranscript f -> {
                return m.withMic(false).with(InterfaceState.TRANSCRIBING, "Heard", f.text());
            }
            case DriftEvent.CommandPlanned c -> {
                return m.with(InterfaceState.PROCESSING, "\"" + c.utterance() + "\"", c.plan()).clearedPending();
            }
            case DriftEvent.ConfirmationRequested c -> {
                cancelDismiss();
                return m.with(InterfaceState.PROCESSING, "Confirm", c.prompt()).withPending(c.pendingId(), c.prompt(), List.of());
            }
            case DriftEvent.ChoiceRequested c -> {
                cancelDismiss();
                return m.with(InterfaceState.PROCESSING, "Choose", c.prompt()).withPending(c.pendingId(), c.prompt(), c.options());
            }
            case DriftEvent.ExecutionStarted s -> {
                return m.with(InterfaceState.EXECUTING, "Working", s.description()).clearedPending();
            }
            case DriftEvent.ExecutionFinished f -> {
                return notify(m, f.success() ? "Done" : "Failed", f.message(), f.success(), NOTIFICATION_MILLIS);
            }
            case DriftEvent.Reply r -> {
                if (m.state() == InterfaceState.EXECUTING) return null;   // ExecutionFinished already reported
                return notify(m, r.ok() ? "Drift" : "Can't do that", r.message(), r.ok(), REPLY_MILLIS);
            }
            case DriftEvent.Notification n -> {
                if (m.state() == InterfaceState.EXECUTING) return m.with(InterfaceState.EXECUTING, m.title(), n.message());
                if (busy || m.state() == InterfaceState.ERROR) return null;
                return notify(m, n.title(), n.message(), true, NOTIFICATION_MILLIS);
            }
            case DriftEvent.Failure f -> {
                cancelDismiss();
                return m.withMic(false).with(InterfaceState.ERROR, f.title(), f.detail()).withRecovery(f.recovery()).withOk(false).clearedPending();
            }
            case DriftEvent.MediaChanged md -> {
                boolean playing = "PLAYING".equals(md.status());
                UiModel withMedia = m.withMedia(md.title(), md.artist(), playing);
                if (m.state() == InterfaceState.IDLE || m.state() == InterfaceState.MUSIC) return base(withMedia);
                return withMedia;
            }
            case DriftEvent.AssistantOpened a -> {
                assistantOpen = true;
                cancelDismiss();
                return m.with(InterfaceState.ASSISTANT, "Assistant", "Type a command or hold the mic button").withOk(true);
            }
            case DriftEvent.DictationChanged d -> {
                UiModel dm = m.withDictation(d.state(), d.target(), d.text());
                return switch (d.state()) {
                    case "PREVIEW" -> { cancelDismiss(); yield dm.with(InterfaceState.ASSISTANT, "Review dictation", "Into: " + d.target()); }
                    case "IDLE" -> m.state() == InterfaceState.ASSISTANT && "PREVIEW".equals(m.dictationState()) ? base(dm) : dm;
                    default -> dm;
                };
            }
            case DriftEvent.SettingsChanged s -> { return null; }
            case DriftEvent.LayoutChanged s -> { return null; }
        }
    }

    /** Dismisses an error/assistant/notification and returns to the base state. */
    public void dismiss() {
        UiModel changed;
        synchronized (this) {
            cancelDismiss();
            assistantOpen = false;
            changed = base(model.clearedPending().withOk(true).withRecovery(null));
            if (changed.equals(model)) return;
            model = changed;
        }
        listener.accept(changed);
    }

    private UiModel base(UiModel m) {
        if (assistantOpen) return m.with(InterfaceState.ASSISTANT, "Assistant", "Type a command or hold the mic button");
        if (m.playing() && m.track() != null) return m.with(InterfaceState.MUSIC, m.track(), m.artist() == null ? "" : m.artist());
        return m.with(InterfaceState.IDLE, "Drift", "Ready").withOk(true);
    }

    private UiModel notify(UiModel m, String title, String detail, boolean ok, long millis) {
        cancelDismiss();
        dismissTask = timer.schedule(() -> {
            UiModel changed;
            synchronized (StateController.this) {
                if (model.state() != InterfaceState.NOTIFICATION) return;
                changed = base(model.withOk(true));
                model = changed;
            }
            listener.accept(changed);
        }, millis, TimeUnit.MILLISECONDS);
        return m.withMic(false).with(InterfaceState.NOTIFICATION, title, detail).withOk(ok).clearedPending();
    }

    private void cancelDismiss() {
        if (dismissTask != null) { dismissTask.cancel(false); dismissTask = null; }
    }
}
