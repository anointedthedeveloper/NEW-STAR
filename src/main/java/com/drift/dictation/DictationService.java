package com.drift.dictation;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Explicit dictation: speech becomes text in a preview; nothing is typed anywhere until the user commits,
 * and only if the same window that was focused when dictation started is still focused.
 */
@Service
public class DictationService {
    public enum State { IDLE, DICTATING, PREVIEW }

    public static class DictationException extends Exception {
        public DictationException(String m) { super(m); }
    }

    private final TextInserter inserter;
    private final ForegroundWindow foreground;
    private final DriftEventBus bus;
    private State state = State.IDLE;
    private ForegroundWindow.Info target;
    private final StringBuilder text = new StringBuilder();
    private boolean lastInsertedByUs;

    public DictationService(TextInserter inserter, ForegroundWindow foreground, DriftEventBus bus) {
        this.inserter = inserter;
        this.foreground = foreground;
        this.bus = bus;
    }

    public synchronized State state() { return state; }
    public synchronized String text() { return text.toString(); }
    public synchronized String targetTitle() { return target == null ? "" : target.title(); }

    public synchronized void start() throws DictationException {
        if (!inserter.supported()) throw new DictationException("Typing into other applications is only supported on Windows.");
        target = foreground.current().orElse(null);
        text.setLength(0);
        state = State.DICTATING;
        publish();
    }

    public synchronized void append(String spoken) {
        if (state != State.DICTATING || spoken == null || spoken.isBlank()) return;
        String processed = Punctuation.apply(spoken);
        if (text.length() > 0 && !text.toString().endsWith("\n")) text.append(' ');
        text.append(processed);
        publish();
    }

    /** Ends listening and moves to preview. With no text, returns to idle. */
    public synchronized void stop() {
        if (state != State.DICTATING) return;
        state = text.length() == 0 ? State.IDLE : State.PREVIEW;
        publish();
    }

    /** Stages text produced by a "type ..." command; it still requires a preview commit. */
    public synchronized void stage(String content) throws DictationException {
        if (!inserter.supported()) throw new DictationException("Typing into other applications is only supported on Windows.");
        target = foreground.current().orElse(null);
        text.setLength(0);
        text.append(Punctuation.apply(content));
        state = State.PREVIEW;
        publish();
    }

    public synchronized void edit(String edited) {
        if (state == State.PREVIEW) { text.setLength(0); text.append(edited); publish(); }
    }

    public synchronized void discard() {
        text.setLength(0);
        state = State.IDLE;
        publish();
    }

    /** Types the preview into the original target. Fails (and keeps the preview) if focus has moved. */
    public synchronized void commit() throws DictationException {
        if (state != State.PREVIEW) throw new DictationException("There is no dictated text to insert.");
        Optional<ForegroundWindow.Info> now = foreground.current();
        if (target == null || now.isEmpty() || now.get().handle() != target.handle()) {
            throw new DictationException("The window that was focused when you started (" + targetTitle()
                    + ") is no longer in front. Click back into it, then insert again - nothing was typed.");
        }
        try {
            inserter.insert(text.toString());
        } catch (IOException e) {
            throw new DictationException("Typing failed: " + e.getMessage());
        }
        lastInsertedByUs = true;
        text.setLength(0);
        state = State.IDLE;
        publish();
    }

    /** Sends Ctrl+Z to the target when the last thing we did was an insertion. Application support varies. */
    public synchronized void undoLastInsert() throws DictationException {
        if (!lastInsertedByUs) throw new DictationException("Nothing inserted by Drift to undo.");
        try {
            inserter.sendUndo();
            lastInsertedByUs = false;
        } catch (IOException e) {
            throw new DictationException("Undo failed: " + e.getMessage());
        }
    }

    private void publish() { bus.publish(new DriftEvent.DictationChanged(state.name(), targetTitle(), text.toString())); }
}
