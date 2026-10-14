package com.drift.actions;

import java.util.Map;

/**
 * A typed, validated request for one registered action.
 * @param confirmPrompt when non-null the safety policy must obtain confirmation with exactly this text.
 */
public record ActionRequest(String actionId, Map<String, String> params, String utterance, Source source,
                            String confirmPrompt, String summary) {
    public ActionRequest {
        params = Map.copyOf(params);
    }

    public String param(String name) { return params.get(name); }

    public ActionRequest withSource(Source s) { return new ActionRequest(actionId, params, utterance, s, confirmPrompt, summary); }
}
