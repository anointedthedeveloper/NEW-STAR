package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drift.config.DriftSettings;
import com.drift.diagnostics.MaintenanceService;
import com.drift.themes.ThemeDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeAndMaintenanceTest {
    @TempDir Path dir;

    @Test void radiusStylesheetFollowsTheTheme() {
        ThemeDefinition t = new ThemeDefinition("Round", true, "#112233", 0.9, "Segoe UI", 13, 20, 1, "compact");
        assertThat(t.radiusCss()).contains("-fx-background-radius: 20px").contains("-fx-background-radius: 14px");
        assertThat(ThemeDefinition.coding().radiusCss()).contains("6px").contains("4px");
        assertThat(t.toRootStyle()).contains("-drift-accent:#112233").contains("-drift-space:4px").doesNotContain("radius");
    }

    @Test void invalidThemesCannotProduceCssInjection() {
        ThemeDefinition evil = new ThemeDefinition("Evil", true, "#112233; -fx-effect: x", 0.9, "Arial'; -fx-x:y", 13, 10, 1, "compact");
        assertThat(evil.validate()).hasSize(2);
        assertThatThrownBy(evil::toRootStyle).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(evil::radiusCss).isInstanceOf(IllegalStateException.class);
    }

    @Test void lightAndDarkThemesDiffer() {
        assertThat(ThemeDefinition.driftLight().toRootStyle()).contains("-drift-text:#10151D");
        assertThat(ThemeDefinition.driftDark().toRootStyle()).contains("-drift-text:#E8ECF3");
    }

    @Test void oldLogsArePurgedAndRecentOnesKept() throws Exception {
        Path old = Files.writeString(dir.resolve("drift-2020-01-01.0.log"), "x");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(40, ChronoUnit.DAYS)));
        Path recent = Files.writeString(dir.resolve("drift.log"), "x");
        Path unrelated = Files.writeString(dir.resolve("notes.txt"), "keep");
        Files.setLastModifiedTime(unrelated, FileTime.from(Instant.now().minus(400, ChronoUnit.DAYS)));
        assertThat(MaintenanceService.purgeLogs(dir, 7)).isEqualTo(1);
        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(unrelated).exists();
    }

    @Test void historyRetentionIsEnforced() {
        TestEnv env = new TestEnv(dir);
        env.history.record("u", "app.open", "{}", "OK", "old", 1);
        env.history.record("u", "app.open", "{}", "OK", "new", 1);
        env.db.update("UPDATE operation_history SET ts=datetime('now','-90 days') WHERE message='old'");
        assertThat(env.history.purgeExpired()).isEqualTo(1); // default retention 30 days
        assertThat(env.history.recent(10)).extracting("message").containsExactly("new");
    }

    @Test void transcriptsAreNotStoredByDefaultAndSecretsAreRedacted() {
        TestEnv env = new TestEnv(dir);
        env.history.record("my password is hunter2", "app.open", "{\"token\":\"x\"}", "OK", "password: hunter2", 1);
        var e = env.history.recent(1).get(0);
        assertThat(e.utterance()).isNull();                       // storeTranscripts is off by default
        assertThat(e.message()).doesNotContain("hunter2");
        env.settings.update(s -> s.withPrivacy(s.privacy().withStoreTranscripts(true)));
        env.history.record("open my password is hunter2 file", "app.open", "{}", "OK", "ok", 1);
        assertThat(env.history.recent(1).get(0).utterance()).doesNotContain("hunter2");
        assertThat(DriftSettings.defaults().privacy().storeTranscripts()).isFalse();
    }
}
