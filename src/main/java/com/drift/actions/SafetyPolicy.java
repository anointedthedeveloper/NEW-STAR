package com.drift.actions;

import com.drift.config.SettingsService;
import org.springframework.stereotype.Component;

/** Decides whether a validated request may run, needs explicit confirmation, or is denied. */
@Component
public class SafetyPolicy {
    public sealed interface Decision {
        record Allow() implements Decision {}
        record Confirm(String prompt) implements Decision {}
        record Deny(String reason) implements Decision {}
    }

    private final SettingsService settings;

    public SafetyPolicy(SettingsService settings) { this.settings = settings; }

    public Decision decide(ActionDefinition def, ActionRequest req) {
        if (def.permission() == Permission.PROCESS && !settings.get().developer().enabled()) {
            return new Decision.Deny("Developer mode is turned off. Enable it in Settings > Developer to run build or test commands.");
        }
        // Learned or chosen mappings never lower the bar: privileged permissions always need confirmation on that path.
        boolean privileged = def.permission() == Permission.PROCESS || def.permission() == Permission.INPUT;
        if (privileged && (req.source() == Source.LEARNED_COMMAND)) {
            return new Decision.Confirm("This came from a learned shortcut. " + (req.confirmPrompt() != null ? req.confirmPrompt() : "Run " + def.description() + "?"));
        }
        if (req.confirmPrompt() != null) return new Decision.Confirm(req.confirmPrompt());
        if (def.confirmation() == ActionDefinition.Confirmation.ALWAYS) return new Decision.Confirm("Confirm: " + def.description() + "?");
        return new Decision.Allow();
    }
}
