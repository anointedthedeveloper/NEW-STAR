package com.drift.themes;

import com.drift.config.DriftSettings;
import com.drift.config.SettingsService;
import com.drift.core.Json;
import com.drift.persistence.Database;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ThemeService {
    private static final long MAX_IMPORT_BYTES = 16 * 1024;
    private final Database db;
    private final SettingsService settings;

    public ThemeService(Database db, SettingsService settings) {
        this.db = db;
        this.settings = settings;
    }

    public Map<String, ThemeDefinition> all() {
        Map<String, ThemeDefinition> out = new LinkedHashMap<>();
        for (ThemeDefinition t : List.of(ThemeDefinition.driftDark(), ThemeDefinition.driftLight(), ThemeDefinition.coding())) out.put(t.name(), t);
        for (String json : db.query("SELECT json FROM themes ORDER BY name", rs -> rs.getString(1))) {
            try {
                ThemeDefinition t = Json.read(json, ThemeDefinition.class);
                if (t.validate().isEmpty()) out.put(t.name(), t);
            } catch (IllegalArgumentException ignored) {
                // corrupt stored theme: skip rather than fail startup
            }
        }
        return out;
    }

    public ThemeDefinition active() {
        ThemeDefinition t = all().get(settings.get().appearance().themeName());
        return t != null ? t : ThemeDefinition.driftDark();
    }

    public void save(ThemeDefinition theme) {
        requireValid(theme);
        db.update("INSERT INTO themes(name,json,updated_at) VALUES(?,?,datetime('now')) "
                + "ON CONFLICT(name) DO UPDATE SET json=excluded.json, updated_at=excluded.updated_at", theme.name(), Json.write(theme));
    }

    public void delete(String name) {
        db.update("DELETE FROM themes WHERE name=?", name);
        if (settings.get().appearance().themeName().equals(name)) apply(ThemeDefinition.driftDark().name());
    }

    public void apply(String name) {
        if (!all().containsKey(name)) throw new IllegalArgumentException("Unknown theme: " + name);
        settings.update(s -> new DriftSettings(new DriftSettings.Appearance(name, s.appearance().reducedMotion(), s.appearance().iconPack()),
                s.floating(), s.voice(), s.startup(), s.developer(), s.privacy(), s.monitoring(), s.shortcuts(), s.weather()));
    }

    public String export(ThemeDefinition theme) {
        requireValid(theme);
        return Json.write(theme);
    }

    /** Imports a theme file after size and content validation. Does not apply it. */
    public ThemeDefinition importFile(Path file) {
        try {
            if (Files.size(file) > MAX_IMPORT_BYTES) throw new IllegalArgumentException("Theme file is larger than " + MAX_IMPORT_BYTES + " bytes");
            ThemeDefinition t = Json.read(Files.readString(file, StandardCharsets.UTF_8), ThemeDefinition.class);
            requireValid(t);
            save(t);
            return t;
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Cannot read theme file: " + e.getMessage(), e);
        }
    }

    private static void requireValid(ThemeDefinition t) {
        List<String> errors = t.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid theme: " + String.join("; ", errors));
    }
}
