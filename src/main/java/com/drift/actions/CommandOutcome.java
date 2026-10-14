package com.drift.actions;

import java.util.List;

/** Honest, user-facing result of processing an utterance. */
public sealed interface CommandOutcome {
    String message();

    record Done(ActionResult result) implements CommandOutcome {
        @Override public String message() { return result.message(); }
    }
    record NeedsConfirmation(String pendingId, String prompt) implements CommandOutcome {
        @Override public String message() { return prompt; }
    }
    record NeedsChoice(String pendingId, String prompt, List<String> options, boolean canRemember) implements CommandOutcome {
        @Override public String message() { return prompt; }
    }
    /** Understood, but not permitted (policy, permissions, validation). */
    record Refused(String reason) implements CommandOutcome {
        @Override public String message() { return reason; }
    }
    /** Recognised or not, Drift cannot do this. */
    record Unsupported(String reason) implements CommandOutcome {
        @Override public String message() { return reason; }
    }
    record Failed(String reason) implements CommandOutcome {
        @Override public String message() { return reason; }
    }
}
