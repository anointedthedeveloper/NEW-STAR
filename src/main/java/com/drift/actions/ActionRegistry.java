package com.drift.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ActionRegistry {
    private final Map<String, ActionDefinition> actions = new LinkedHashMap<>();

    public synchronized void register(ActionDefinition d) {
        if (actions.putIfAbsent(d.id(), d) != null) throw new IllegalStateException("Duplicate action id " + d.id());
    }

    public synchronized Optional<ActionDefinition> find(String id) { return Optional.ofNullable(actions.get(id)); }

    public synchronized Collection<ActionDefinition> all() { return List.copyOf(actions.values()); }

    /** Validation errors for a request against its definition; empty when valid. */
    public List<String> validate(ActionRequest r) {
        List<String> errors = new ArrayList<>();
        ActionDefinition d = find(r.actionId()).orElse(null);
        if (d == null) { errors.add("Unknown action " + r.actionId()); return errors; }
        for (ActionDefinition.ParamSpec p : d.params()) {
            String v = r.param(p.name());
            if (v == null || v.isBlank()) { if (p.required()) errors.add("Missing parameter: " + p.name()); continue; }
            if (v.length() > p.maxLength()) errors.add("Parameter too long: " + p.name());
            if (v.indexOf('\0') >= 0) errors.add("Parameter contains invalid characters: " + p.name());
        }
        for (String k : r.params().keySet()) {
            if (d.params().stream().noneMatch(p -> p.name().equals(k))) errors.add("Unexpected parameter: " + k);
        }
        return errors;
    }
}
