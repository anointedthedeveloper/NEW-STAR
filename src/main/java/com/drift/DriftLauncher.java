package com.drift;

import javafx.application.Application;

/**
 * Entry point. Deliberately does not extend {@link Application}: that lets the app start from the plain classpath
 * (jpackage / java -jar) without the JavaFX module-path launcher check.
 */
public final class DriftLauncher {
    private DriftLauncher() {}

    public static void main(String[] args) {
        Application.launch(com.drift.ui.DriftFxApp.class, args);
    }
}
