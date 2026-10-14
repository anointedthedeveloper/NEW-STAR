package com.drift.windows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves well-known user folders, using the Windows Known Folder API when available (handles OneDrive redirection). */
public final class KnownFolders {
    private KnownFolders() {}

    public static Map<String, Path> standard() {
        Path home = Path.of(System.getProperty("user.home"));
        Map<String, Path> out = new LinkedHashMap<>();
        out.put("downloads", resolve("FOLDERID_Downloads", home.resolve("Downloads")));
        out.put("documents", resolve("FOLDERID_Documents", home.resolve("Documents")));
        out.put("desktop", resolve("FOLDERID_Desktop", home.resolve("Desktop")));
        out.put("pictures", resolve("FOLDERID_Pictures", home.resolve("Pictures")));
        out.put("music", resolve("FOLDERID_Music", home.resolve("Music")));
        out.put("videos", resolve("FOLDERID_Videos", home.resolve("Videos")));
        out.put("home", home);
        out.values().removeIf(p -> !Files.isDirectory(p));
        return out;
    }

    private static Path resolve(String knownFolderField, Path fallback) {
        if (!PlatformInfo.isWindows()) return fallback;
        try {
            var field = com.sun.jna.platform.win32.KnownFolders.class.getField(knownFolderField);
            var guid = (com.sun.jna.platform.win32.Guid.GUID) field.get(null);
            String path = com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(guid);
            if (path != null && !path.isBlank()) return Path.of(path);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // fall through to the conventional location
        }
        return fallback;
    }
}
