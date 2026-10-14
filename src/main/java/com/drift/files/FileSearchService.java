package com.drift.files;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Bounded, explicit-root file search. Never walks outside the roots it is given. */
@Service
public class FileSearchService {
    public static final int MAX_DEPTH = 6;
    public static final int MAX_VISITED = 100_000;
    public static final int MAX_RESULTS = 25;
    private static final long MAX_MILLIS = 5_000;
    private static final Set<String> PROJECT_MARKERS = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle");

    public record SearchResult(List<Path> matches, boolean truncated, int visited) {}

    public SearchResult searchByName(String query, List<Path> roots) {
        List<String> tokens = tokens(query);
        if (tokens.isEmpty()) return new SearchResult(List.of(), false, 0);
        return walk(roots, false, (path, isDir) -> {
            String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
            return tokens.stream().allMatch(name::contains);
        });
    }

    /** Finds directories holding a Maven/Gradle build file. */
    public SearchResult findJavaProjects(List<Path> roots) {
        return walk(roots, true, (path, isDir) -> isDir && PROJECT_MARKERS.stream().anyMatch(m -> Files.isRegularFile(path.resolve(m))));
    }

    private interface Matcher { boolean matches(Path path, boolean isDir); }

    private SearchResult walk(List<Path> roots, boolean directoriesOnly, Matcher matcher) {
        List<Path> out = new ArrayList<>();
        int[] visited = {0};
        boolean[] truncated = {false};
        long deadline = System.currentTimeMillis() + MAX_MILLIS;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try {
                Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
                        if (budgetExceeded()) return FileVisitResult.TERMINATE;
                        if (a.isSymbolicLink() || (!dir.equals(root) && skip(dir))) return FileVisitResult.SKIP_SUBTREE;
                        visited[0]++;
                        if (!dir.equals(root) && matcher.matches(dir, true)) {
                            out.add(dir);
                            if (out.size() >= MAX_RESULTS) { truncated[0] = true; return FileVisitResult.TERMINATE; }
                            if (directoriesOnly) return FileVisitResult.SKIP_SUBTREE; // do not list nested modules separately
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                        if (budgetExceeded()) return FileVisitResult.TERMINATE;
                        visited[0]++;
                        if (!directoriesOnly && a.isRegularFile() && !skip(file) && matcher.matches(file, false)) {
                            out.add(file);
                            if (out.size() >= MAX_RESULTS) { truncated[0] = true; return FileVisitResult.TERMINATE; }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFileFailed(Path file, IOException e) { return FileVisitResult.CONTINUE; }
                    private boolean budgetExceeded() {
                        if (visited[0] >= MAX_VISITED || System.currentTimeMillis() > deadline) { truncated[0] = true; return true; }
                        return false;
                    }
                });
            } catch (IOException | SecurityException ignored) {
                // unreadable root: skip it
            }
            if (truncated[0]) break;
        }
        return new SearchResult(List.copyOf(out), truncated[0], visited[0]);
    }

    private static boolean skip(Path p) {
        Path name = p.getFileName();
        if (name == null) return false;
        String n = name.toString();
        return n.startsWith(".") || n.equals("node_modules") || n.equals("target") || PathValidator.isExcludedName(n);
    }

    static List<String> tokens(String query) {
        if (query == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String t : query.toLowerCase(Locale.ROOT).split("\\s+")) if (!t.isBlank()) out.add(t);
        return out;
    }
}
