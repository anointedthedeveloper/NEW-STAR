package com.drift.ui;

import com.drift.core.DriftEvent;
import com.drift.themes.ThemeDefinition;
import com.drift.themes.ThemeService;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Parent;
import javafx.scene.Scene;

/** Applies the active theme to every registered scene so all windows stay consistent. */
public class ThemeApplier {
    private final ThemeService themes;
    private final List<Scene> scenes = new ArrayList<>();
    private java.nio.file.Path tempDir;
    private final String stylesheet = ThemeApplier.class.getResource("/ui/drift.css").toExternalForm();

    public ThemeApplier(ThemeService themes, com.drift.core.DriftEventBus bus) {
        this.themes = themes;
        bus.subscribe(e -> { if (e instanceof DriftEvent.SettingsChanged) Fx.ui(this::applyAll); });
    }

    public void register(Scene scene) {
        scenes.add(scene);
        apply(scene);
    }

    public ThemeDefinition theme() { return themes.active(); }

    public void applyAll() { scenes.forEach(this::apply); }

    private void apply(Scene scene) {
        ThemeDefinition t = themes.active();
        if (!scene.getStylesheets().contains(stylesheet)) scene.getStylesheets().add(stylesheet);
        String generated = generatedSheet(t);
        scene.getStylesheets().removeIf(s -> s.contains("drift-theme-") && !s.equals(generated));
        if (generated != null && !scene.getStylesheets().contains(generated)) scene.getStylesheets().add(generated);
        Parent root = scene.getRoot();
        if (root != null) root.setStyle(t.toRootStyle());
    }

    /** Writes (once per distinct radius) the generated corner-radius stylesheet and returns its URL. */
    private String generatedSheet(ThemeDefinition t) {
        String css = t.radiusCss();
        try {
            if (tempDir == null) { tempDir = java.nio.file.Files.createTempDirectory("drift-theme-"); tempDir.toFile().deleteOnExit(); }
            java.nio.file.Path f = tempDir.resolve("drift-theme-" + Integer.toHexString(css.hashCode()) + ".css");
            if (!java.nio.file.Files.exists(f)) { java.nio.file.Files.writeString(f, css); f.toFile().deleteOnExit(); }
            return f.toUri().toString();
        } catch (java.io.IOException e) {
            return null;
        }
    }
}
