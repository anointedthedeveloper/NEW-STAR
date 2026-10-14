package com.drift.learning;

import com.drift.core.Json;
import com.drift.learning.AliasRepository.Alias;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.persistence.Database;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Local personalisation. Learning is deliberately limited to low-risk actions: nothing that executes
 * processes or injects keystrokes can ever be learned, and a learned mapping never bypasses the safety policy.
 */
@Service
public class LearningService {
    /** Actions whose mappings may be remembered. Excludes dev.run, text.type and anything privileged. */
    public static final Set<String> LEARNABLE_ACTIONS = Set.of("app.open", "folder.open", "file.open", "project.open", "media.control", "theme.set");
    static final int EXPORT_VERSION = 1;
    private static final int MAX_IMPORT_ITEMS = 2000;

    private final AliasRepository aliases;
    private final LearnedCommandRepository commands;
    private final Database db;

    public LearningService(AliasRepository aliases, LearnedCommandRepository commands, Database db) {
        this.aliases = aliases;
        this.commands = commands;
        this.db = db;
    }

    public static boolean canLearn(String actionId) { return LEARNABLE_ACTIONS.contains(actionId); }

    public Optional<Alias> existing(AliasKind kind, String phrase) { return aliases.find(kind, phrase); }

    /** Stores an alias. Returns the previous target if one was replaced; callers confirm with the user first. */
    public Optional<String> remember(AliasKind kind, String phrase, String target) {
        Optional<String> previous = aliases.find(kind, phrase).map(Alias::target);
        aliases.put(kind, phrase, target);
        return previous;
    }

    public void recordSuccess(String normalizedUtterance, String actionId, Map<String, String> params) {
        if (!canLearn(actionId)) return;
        commands.recordSuccess(normalizedUtterance, actionId, Json.write(params));
    }

    public Optional<LearnedCommandRepository.LearnedCommand> lookup(String normalizedUtterance) {
        return commands.find(normalizedUtterance).filter(c -> !c.disabled() && canLearn(c.actionId()));
    }

    public void recordCorrection(String utterance, String wrongAction, String rightAction) {
        db.update("INSERT INTO corrections(utterance,wrong_action,right_action) VALUES(?,?,?)", utterance, wrongAction, rightAction);
    }

    public void reject(String normalizedUtterance) { commands.recordReject(normalizedUtterance); }

    public void reset() {
        aliases.deleteAll();
        commands.deleteAll();
        db.update("DELETE FROM corrections");
    }

    public String exportJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", EXPORT_VERSION);
        List<Map<String, String>> a = new ArrayList<>();
        for (Alias al : aliases.listAll()) a.add(Map.of("kind", al.kind().name(), "phrase", al.phrase(), "target", al.target()));
        out.put("aliases", a);
        List<Map<String, String>> c = new ArrayList<>();
        for (var lc : commands.list()) c.add(Map.of("utterance", lc.utterance(), "actionId", lc.actionId(), "params", lc.paramsJson()));
        out.put("commands", c);
        return Json.write(out);
    }

    /** Imports exported data. Entries that fail validation are skipped; returns how many were imported. */
    public int importJson(String json) {
        @SuppressWarnings("unchecked") Map<String, Object> root = Json.read(json, Map.class);
        Object version = root.get("version");
        if (!(version instanceof Number n) || n.intValue() != EXPORT_VERSION) throw new IllegalArgumentException("Unsupported export version: " + version);
        int count = 0;
        if (root.get("aliases") instanceof List<?> list) {
            if (list.size() > MAX_IMPORT_ITEMS) throw new IllegalArgumentException("Too many aliases in import");
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    try {
                        aliases.put(AliasKind.valueOf(String.valueOf(m.get("kind"))), String.valueOf(m.get("phrase")), String.valueOf(m.get("target")));
                        count++;
                    } catch (IllegalArgumentException ignored) {
                        // skip invalid entry
                    }
                }
            }
        }
        if (root.get("commands") instanceof List<?> list) {
            if (list.size() > MAX_IMPORT_ITEMS) throw new IllegalArgumentException("Too many commands in import");
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && canLearn(String.valueOf(m.get("actionId")))) {
                    commands.recordSuccess(String.valueOf(m.get("utterance")), String.valueOf(m.get("actionId")), String.valueOf(m.get("params")));
                    count++;
                }
            }
        }
        return count;
    }
}
