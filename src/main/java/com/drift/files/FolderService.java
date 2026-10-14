package com.drift.files;

import com.drift.config.SettingsService;
import com.drift.learning.AliasRepository;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.windows.KnownFolders;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Resolves spoken folder names (user aliases first, then standard folders) and defines which roots Drift may touch. */
@Service
public class FolderService {
    private final AliasRepository aliases;
    private final SettingsService settings;

    public FolderService(AliasRepository aliases, SettingsService settings) {
        this.aliases = aliases;
        this.settings = settings;
    }

    public Optional<Path> resolve(String spoken) {
        String key = spoken.toLowerCase(Locale.ROOT).strip();
        Optional<AliasRepository.Alias> alias = aliases.find(AliasKind.FOLDER, key);
        if (alias.isEmpty()) alias = aliases.find(AliasKind.FOLDER, "my " + key); // "open my X folder" is parsed as X
        if (alias.isPresent()) {
            Path p = Path.of(alias.get().target());
            if (Files.isDirectory(p)) return Optional.of(p);
        }
        Map<String, Path> std = KnownFolders.standard();
        String singular = key.endsWith("s") ? key.substring(0, key.length() - 1) : key;
        for (String candidate : List.of(key, key + "s", singular)) {
            if (std.containsKey(candidate)) return Optional.of(std.get(candidate));
        }
        return Optional.empty();
    }

    /** Roots that file/folder opening is permitted in: user's standard folders, dev roots and explicit folder aliases. */
    public List<Path> allowedRoots() {
        List<Path> roots = new ArrayList<>(KnownFolders.standard().values());
        for (String r : settings.get().developer().projectRoots()) roots.add(Path.of(r));
        for (AliasRepository.Alias a : aliases.list(AliasKind.FOLDER)) if (!a.disabled()) roots.add(Path.of(a.target()));
        return roots;
    }

    /** Roots used for searching: the common user document folders plus dev roots - never whole drives. */
    public List<Path> searchRoots() {
        List<Path> roots = new ArrayList<>();
        Map<String, Path> std = KnownFolders.standard();
        for (String k : List.of("documents", "desktop", "downloads")) if (std.containsKey(k)) roots.add(std.get(k));
        for (String r : settings.get().developer().projectRoots()) roots.add(Path.of(r));
        return roots;
    }
}
