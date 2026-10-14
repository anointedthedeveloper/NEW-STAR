package com.drift.widgets;

import com.drift.core.Json;
import com.drift.persistence.Database;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Saves, restores and switches widget layouts (presets). The previous state of a layout is kept for one-step restore. */
@Service
public class LayoutService {
    static final String ACTIVE_KEY = "widgets.activeLayout";
    static final String DEFAULT_NAME = "Default";
    private static final String PREVIOUS_SUFFIX = " (previous)";
    private final Database db;

    public LayoutService(Database db) { this.db = db; }

    public List<String> names() {
        return db.query("SELECT name FROM widget_layouts WHERE name NOT LIKE '% (previous)' ORDER BY name", rs -> rs.getString(1));
    }

    public Optional<WidgetLayout> load(String name) {
        return db.queryOne("SELECT json FROM widget_layouts WHERE name=?", rs -> rs.getString(1), name).flatMap(json -> {
            try {
                WidgetLayout l = Json.read(json, WidgetLayout.class);
                return l.validate().isEmpty() ? Optional.of(l) : Optional.empty();
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    public String activeName() {
        return db.queryOne("SELECT value FROM kv WHERE key=?", rs -> rs.getString(1), ACTIVE_KEY).orElse(DEFAULT_NAME);
    }

    public WidgetLayout active() {
        return load(activeName()).orElseGet(() -> new WidgetLayout(activeName(), List.of()));
    }

    public void setActive(String name) {
        db.update("INSERT INTO kv(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value", ACTIVE_KEY, name);
    }

    public void save(WidgetLayout layout) {
        List<String> errors = layout.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid layout: " + String.join("; ", errors));
        // keep the old copy so "restore previous" works
        load(layout.name()).ifPresent(old -> put(layout.name() + PREVIOUS_SUFFIX, old));
        put(layout.name(), layout);
    }

    public Optional<WidgetLayout> restorePrevious(String name) {
        Optional<WidgetLayout> prev = load(name + PREVIOUS_SUFFIX).map(l -> new WidgetLayout(name, l.widgets()));
        prev.ifPresent(l -> put(name, l));
        return prev;
    }

    public void delete(String name) {
        db.update("DELETE FROM widget_layouts WHERE name IN (?,?)", name, name + PREVIOUS_SUFFIX);
    }

    private void put(String name, WidgetLayout layout) {
        db.update("INSERT INTO widget_layouts(name,json,updated_at) VALUES(?,?,datetime('now')) "
                + "ON CONFLICT(name) DO UPDATE SET json=excluded.json, updated_at=excluded.updated_at", name, Json.write(new WidgetLayout(name.replace(PREVIOUS_SUFFIX, ""), layout.widgets())));
    }
}
