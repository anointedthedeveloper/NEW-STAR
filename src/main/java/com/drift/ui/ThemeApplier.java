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
        if (!scene.getStylesheets().contains(stylesheet)) scene.getStylesheets().add(stylesheet);
        Parent root = scene.getRoot();
        if (root != null) root.setStyle(themes.active().toRootStyle());
    }
}
