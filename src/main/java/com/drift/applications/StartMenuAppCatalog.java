package com.drift.applications;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Discovers applications from Windows Start Menu shortcut folders (the same list Windows shows users).
 * Entries are the .lnk files themselves; launching hands the shortcut to the shell, so no target parsing
 * or command lines are ever constructed by Drift.
 */
public class StartMenuAppCatalog implements AppCatalog {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_ENTRIES = 3000;
    private final List<Path> roots;

    public StartMenuAppCatalog(List<Path> roots) { this.roots = roots; }

    public static List<Path> defaultRoots() {
        List<Path> roots = new ArrayList<>();
        String programData = System.getenv("ProgramData");
        String appData = System.getenv("APPDATA");
        if (programData != null) roots.add(Path.of(programData, "Microsoft", "Windows", "Start Menu", "Programs"));
        if (appData != null) roots.add(Path.of(appData, "Microsoft", "Windows", "Start Menu", "Programs"));
        return roots;
    }

    @Override
    public List<AppEntry> scan() {
        Map<String, AppEntry> byName = new HashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root, MAX_DEPTH)) {
                s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".lnk"))
                        .filter(Files::isRegularFile)
                        .limit(MAX_ENTRIES)
                        .forEach(p -> {
                            String file = p.getFileName().toString();
                            String name = file.substring(0, file.length() - 4);
                            if (name.toLowerCase(Locale.ROOT).contains("uninstall")) return;
                            byName.putIfAbsent(name.toLowerCase(Locale.ROOT), new AppEntry(idFor(p), name, p.toAbsolutePath().toString()));
                        });
            } catch (IOException | RuntimeException ignored) {
                // unreadable folder: skip
            }
        }
        List<AppEntry> out = new ArrayList<>(byName.values());
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    static String idFor(Path p) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(p.toAbsolutePath().toString().toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
