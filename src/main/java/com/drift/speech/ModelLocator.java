package com.drift.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** Finds Vosk model folders under the Drift models directory. Models are never shipped in the repository or installer. */
public class ModelLocator {
    private final Path modelsDir;

    public ModelLocator(Path modelsDir) { this.modelsDir = modelsDir; }

    public static boolean looksLikeVoskModel(Path dir) {
        return Files.isDirectory(dir.resolve("am")) && Files.isDirectory(dir.resolve("conf"));
    }

    public List<Path> installed() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(modelsDir)) return out;
        try (Stream<Path> s = Files.list(modelsDir)) {
            s.filter(Files::isDirectory).filter(ModelLocator::looksLikeVoskModel).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // treat as none installed
        }
        return out;
    }

    /** The configured model if valid, else the installed model best matching the language (e.g. en-US -> "...-en-us-..."). */
    public Optional<Path> select(String configuredPath, String language) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path p = Path.of(configuredPath);
            return looksLikeVoskModel(p) ? Optional.of(p) : Optional.empty();
        }
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        String shortLang = lang.contains("-") ? lang.substring(0, lang.indexOf('-')) : lang;
        List<Path> all = installed();
        return all.stream().filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("-" + lang + "-")).findFirst()
                .or(() -> all.stream().filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("-" + shortLang + "-")).findFirst());
    }

    public Path modelsDir() { return modelsDir; }
}
