package com.drift.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Validates every path that originates from speech, learned data or imports before it is touched.
 * Paths must exist, resolve (through symlinks) inside an allowed root, avoid system directories, and files
 * with executable/script extensions are never opened by voice.
 */
public class PathValidator {
    public static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "scr", "msi", "msp", "ps1", "psm1", "vbs", "vbe", "js", "jse", "wsf", "wsh",
            "lnk", "pif", "reg", "hta", "cpl", "jar", "dll", "sys", "sh", "appx", "msix");
    private static final Set<String> EXCLUDED_SEGMENTS = Set.of(
            "windows", "system32", "syswow64", "program files", "program files (x86)", "programdata",
            "$recycle.bin", "system volume information", "appdata", ".ssh", ".gnupg", ".aws");

    private final Supplier<List<Path>> allowedRoots;

    public PathValidator(Supplier<List<Path>> allowedRoots) { this.allowedRoots = allowedRoots; }

    public static class PathRejectedException extends RuntimeException {
        public PathRejectedException(String m) { super(m); }
    }

    /** Returns the canonical path of an existing file or directory that is permitted. */
    public Path validateExisting(String raw) {
        if (raw == null || raw.isBlank()) throw new PathRejectedException("Path is empty");
        if (raw.length() > 1024 || raw.indexOf('\0') >= 0) throw new PathRejectedException("Path is malformed");
        Path real;
        try {
            real = Path.of(raw).toRealPath();
        } catch (InvalidPathException e) {
            throw new PathRejectedException("Path is not valid");
        } catch (IOException e) {
            throw new PathRejectedException("Path does not exist or is not accessible");
        }
        return checkAllowed(real);
    }

    public Path checkAllowed(Path real) {
        boolean inside = false;
        for (Path root : allowedRoots.get()) {
            Path r = realOrSelf(root);
            if (real.startsWith(r)) {
                inside = true;
                // exclusions are judged relative to the root so that e.g. a root under "ProgramData" is not self-excluding
                Path relative = r.relativize(real);
                for (Path seg : relative) {
                    if (EXCLUDED_SEGMENTS.contains(seg.toString().toLowerCase(Locale.ROOT))) {
                        throw new PathRejectedException("That location is excluded for safety");
                    }
                }
                break;
            }
        }
        if (!inside) throw new PathRejectedException("That location is outside the folders Drift is allowed to use");
        return real;
    }

    public Path validateDirectory(String raw) {
        Path p = validateExisting(raw);
        if (!Files.isDirectory(p)) throw new PathRejectedException("Not a folder");
        return p;
    }

    /** A file Drift may hand to its associated application. Scripts and executables are refused. */
    public Path validateOpenableFile(String raw) {
        Path p = validateExisting(raw);
        if (!Files.isRegularFile(p)) throw new PathRejectedException("Not a regular file");
        if (BLOCKED_EXTENSIONS.contains(extension(p))) {
            throw new PathRejectedException("Drift does not open executable or script files by voice (." + extension(p) + ")");
        }
        return p;
    }

    public static String extension(Path p) {
        String n = p.getFileName() == null ? "" : p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isExcludedName(String segment) {
        return EXCLUDED_SEGMENTS.contains(segment.toLowerCase(Locale.ROOT));
    }

    private static Path realOrSelf(Path p) {
        try { return p.toRealPath(); } catch (IOException e) { return p.toAbsolutePath().normalize(); }
    }
}
