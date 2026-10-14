package com.drift.core;

import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves Drift's local data directory. Override with -Ddrift.home=... (used by tests). */
public final class DriftPaths {
    private final Path home;

    public DriftPaths(Path home) {
        this.home = home.toAbsolutePath().normalize();
    }

    public static DriftPaths resolve() {
        String override = System.getProperty("drift.home");
        if (override != null && !override.isBlank()) {
            return new DriftPaths(Path.of(override));
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return new DriftPaths(Path.of(appData, "Drift"));
        }
        return new DriftPaths(Path.of(System.getProperty("user.home"), ".drift"));
    }

    public Path home() { return home; }
    public Path database() { return home.resolve("drift.db"); }
    public Path logs() { return home.resolve("logs"); }
    public Path models() { return home.resolve("models"); }
    public Path packs() { return home.resolve("packs"); }

    public DriftPaths ensureCreated() {
        try {
            Files.createDirectories(home);
            Files.createDirectories(logs());
            Files.createDirectories(models());
            Files.createDirectories(packs());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create Drift data directory " + home, e);
        }
        return this;
    }
}
