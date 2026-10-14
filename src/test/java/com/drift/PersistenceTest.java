package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drift.config.DriftSettings;
import com.drift.config.SettingsService;
import com.drift.core.DriftEventBus;
import com.drift.persistence.Database;
import com.drift.themes.ThemeDefinition;
import com.drift.themes.ThemeService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersistenceTest {
    @TempDir Path dir;

    @Test void migrationsCreateSchemaAndAreIdempotent() {
        Database db = new Database(dir.resolve("a.db"));
        new Database(dir.resolve("a.db")); // reopening must not re-apply
        assertThat(db.query("SELECT version FROM schema_migrations ORDER BY version", rs -> rs.getInt(1))).containsExactly(1, 2, 3);
        assertThat(db.query("SELECT name FROM sqlite_master WHERE type='table'", rs -> rs.getString(1)))
                .contains("kv", "themes", "widget_layouts", "operation_history", "aliases", "learned_commands", "app_usage", "dev_projects");
    }

    @Test void settingsPersistAcrossRestartAndDefaultsFillMissingFields() {
        Database db = new Database(dir.resolve("s.db"));
        SettingsService s = new SettingsService(db, new DriftEventBus());
        s.update(x -> new DriftSettings(x.appearance(), new DriftSettings.Floating(true, false, 0.7, 400, 80, "LEFT", 10, 20, true, false, true),
                x.voice(), x.startup(), x.developer(), x.privacy(), x.monitoring(), x.shortcuts(), x.weather()));
        SettingsService reloaded = new SettingsService(new Database(dir.resolve("s.db")), new DriftEventBus());
        assertThat(reloaded.get().floating().opacity()).isEqualTo(0.7);
        assertThat(reloaded.get().floating().anchor()).isEqualTo("LEFT");
        // older stored JSON lacking newer sections still loads
        db.update("UPDATE kv SET value=? WHERE key='settings'", "{\"floating\":{\"opacity\":0.5}}");
        SettingsService partial = new SettingsService(db, new DriftEventBus());
        assertThat(partial.get().floating().opacity()).isEqualTo(0.5);
        assertThat(partial.get().voice().language()).isEqualTo("en-US");
    }

    @Test void invalidSettingsAreRejectedWithoutChangingAnything() {
        SettingsService s = new SettingsService(new Database(dir.resolve("v.db")), new DriftEventBus());
        assertThatThrownBy(() -> s.update(x -> new DriftSettings(x.appearance(), new DriftSettings.Floating(true, true, 5.0, 10, 10, "MIDDLE", 0, 0, false, false, false),
                x.voice(), x.startup(), x.developer(), x.privacy(), x.monitoring(), new DriftSettings.Shortcuts("nonsense", "", ""), x.weather())))
                .isInstanceOf(SettingsService.InvalidSettingsException.class)
                .satisfies(e -> assertThat(((SettingsService.InvalidSettingsException) e).errors()).hasSizeGreaterThanOrEqualTo(4));
        assertThat(s.get()).isEqualTo(DriftSettings.defaults());
    }

    @Test void resetRestoresDefaults() {
        SettingsService s = new SettingsService(new Database(dir.resolve("r.db")), new DriftEventBus());
        s.update(x -> new DriftSettings(x.appearance(), x.floating(), x.voice(), new DriftSettings.Startup(true, false, false), x.developer(), x.privacy(), x.monitoring(), x.shortcuts(), x.weather()));
        assertThat(s.get().startup().launchWithWindows()).isTrue();
        assertThat(s.resetToDefaults()).isEqualTo(DriftSettings.defaults());
    }

    @Test void themesRoundTripAndValidate() throws Exception {
        Database db = new Database(dir.resolve("t.db"));
        ThemeService themes = new ThemeService(db, new SettingsService(db, new DriftEventBus()));
        ThemeDefinition custom = new ThemeDefinition("Ocean", true, "#00AAFF", 0.8, "Consolas", 14, 8, 0.5, "compact");
        Path file = dir.resolve("ocean.json");
        Files.writeString(file, themes.export(custom));
        ThemeDefinition imported = themes.importFile(file);
        assertThat(imported).isEqualTo(custom);
        assertThat(themes.all()).containsKey("Ocean");
        themes.apply("Ocean");
        assertThat(themes.active().accent()).isEqualTo("#00AAFF");
        assertThat(custom.toRootStyle()).contains("-drift-accent:#00AAFF");

        Path bad = Files.writeString(dir.resolve("bad.json"), "{\"name\":\"X\",\"accent\":\"red; -fx-effect: evil\",\"fontFamily\":\"a');}\"}");
        assertThatThrownBy(() -> themes.importFile(bad)).isInstanceOf(IllegalArgumentException.class);
        Path big = Files.writeString(dir.resolve("big.json"), "x".repeat(20_000));
        assertThatThrownBy(() -> themes.importFile(big)).hasMessageContaining("larger");
        assertThat(themes.all()).doesNotContainKey("X");
    }
}
