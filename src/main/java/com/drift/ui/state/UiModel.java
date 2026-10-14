package com.drift.ui.state;

import java.util.List;

/** Everything the floating window needs to render, derived solely from application events. */
public record UiModel(InterfaceState state, String title, String detail, boolean micActive, double level, boolean ok,
                      String pendingId, String pendingPrompt, List<String> options, String recovery,
                      String dictationState, String dictationTarget, String dictationText,
                      String track, String artist, boolean playing) {

    public static UiModel idle() {
        return new UiModel(InterfaceState.IDLE, "Drift", "Ready", false, 0, true, null, null, List.of(), null, "IDLE", "", "", null, null, false);
    }

    UiModel with(InterfaceState s, String title, String detail) {
        return new UiModel(s, title, detail, micActive, level, ok, pendingId, pendingPrompt, options, recovery, dictationState, dictationTarget, dictationText, track, artist, playing);
    }
    UiModel withMic(boolean m) { return new UiModel(state, title, detail, m, m ? level : 0, ok, pendingId, pendingPrompt, options, recovery, dictationState, dictationTarget, dictationText, track, artist, playing); }
    UiModel withLevel(double l) { return new UiModel(state, title, detail, micActive, l, ok, pendingId, pendingPrompt, options, recovery, dictationState, dictationTarget, dictationText, track, artist, playing); }
    UiModel withOk(boolean o) { return new UiModel(state, title, detail, micActive, level, o, pendingId, pendingPrompt, options, recovery, dictationState, dictationTarget, dictationText, track, artist, playing); }
    UiModel withPending(String id, String prompt, List<String> opts) { return new UiModel(state, title, detail, micActive, level, ok, id, prompt, opts, recovery, dictationState, dictationTarget, dictationText, track, artist, playing); }
    UiModel withRecovery(String r) { return new UiModel(state, title, detail, micActive, level, ok, pendingId, pendingPrompt, options, r, dictationState, dictationTarget, dictationText, track, artist, playing); }
    UiModel withDictation(String s, String t, String text) { return new UiModel(state, title, detail, micActive, level, ok, pendingId, pendingPrompt, options, recovery, s, t, text, track, artist, playing); }
    UiModel withMedia(String tr, String ar, boolean p) { return new UiModel(state, title, detail, micActive, level, ok, pendingId, pendingPrompt, options, recovery, dictationState, dictationTarget, dictationText, tr, ar, p); }
    UiModel clearedPending() { return withPending(null, null, List.of()); }
}
