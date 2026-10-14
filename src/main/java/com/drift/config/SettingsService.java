package com.drift.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.drift.core.Json;
import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.persistence.Database;
import java.util.List;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Service;

/** Loads, validates and persists {@link DriftSettings}. Stored JSON is merged over defaults so new fields never break old data. */
@Service
public class SettingsService {
    static final String KEY = "settings";
    private final Database db;
    private final DriftEventBus bus;
    private volatile DriftSettings current;

    public SettingsService(Database db, DriftEventBus bus) {
        this.db = db;
        this.bus = bus;
        this.current = load();
    }

    public DriftSettings get() { return current; }

    /** Applies and persists a change; throws {@link InvalidSettingsException} (without changing anything) if it is invalid. */
    public synchronized DriftSettings update(UnaryOperator<DriftSettings> change) {
        DriftSettings next = change.apply(current);
        List<String> errors = next.validate();
        if (!errors.isEmpty()) throw new InvalidSettingsException(errors);
        persist(next);
        current = next;
        bus.publish(new DriftEvent.SettingsChanged());
        return next;
    }

    public synchronized DriftSettings resetToDefaults() {
        DriftSettings d = DriftSettings.defaults();
        persist(d);
        current = d;
        bus.publish(new DriftEvent.SettingsChanged());
        return d;
    }

    private void persist(DriftSettings s) {
        db.update("INSERT INTO kv(key,value,updated_at) VALUES(?,?,datetime('now')) "
                + "ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at", KEY, Json.write(s));
    }

    private DriftSettings load() {
        var stored = db.queryOne("SELECT value FROM kv WHERE key=?", rs -> rs.getString(1), KEY);
        if (stored.isEmpty()) return DriftSettings.defaults();
        try {
            ObjectNode merged = Json.MAPPER.valueToTree(DriftSettings.defaults());
            merge(merged, Json.MAPPER.readTree(stored.get()));
            DriftSettings s = Json.MAPPER.treeToValue(merged, DriftSettings.class);
            return s.validate().isEmpty() ? s : DriftSettings.defaults();
        } catch (Exception e) {
            return DriftSettings.defaults();
        }
    }

    private static void merge(ObjectNode base, JsonNode overlay) {
        overlay.fields().forEachRemaining(e -> {
            JsonNode existing = base.get(e.getKey());
            if (existing instanceof ObjectNode o && e.getValue().isObject()) merge(o, e.getValue());
            else if (existing != null) base.set(e.getKey(), e.getValue());
        });
    }

    public static class InvalidSettingsException extends RuntimeException {
        private final List<String> errors;
        public InvalidSettingsException(List<String> errors) {
            super("Invalid settings: " + String.join("; ", errors));
            this.errors = List.copyOf(errors);
        }
        public List<String> errors() { return errors; }
    }
}
