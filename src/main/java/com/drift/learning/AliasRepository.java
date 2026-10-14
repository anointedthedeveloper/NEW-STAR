package com.drift.learning;

import com.drift.persistence.Database;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class AliasRepository {
    public enum AliasKind { APP, FOLDER, PHRASE }

    public record Alias(long id, AliasKind kind, String phrase, String target, int usageCount, double confidence,
                        boolean disabled, String createdAt, String lastUsed) {}

    private static final int MAX_PHRASE = 80;
    private static final int MAX_TARGET = 1024;
    private final Database db;

    public AliasRepository(Database db) { this.db = db; }

    private static Alias map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Alias(rs.getLong("id"), AliasKind.valueOf(rs.getString("kind")), rs.getString("phrase"), rs.getString("target"),
                rs.getInt("usage_count"), rs.getDouble("confidence"), rs.getInt("disabled") != 0, rs.getString("created_at"), rs.getString("last_used"));
    }

    public static String key(String phrase) { return phrase.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " "); }

    public Optional<Alias> find(AliasKind kind, String phrase) {
        return db.queryOne("SELECT * FROM aliases WHERE kind=? AND phrase=? AND disabled=0", AliasRepository::map, kind.name(), key(phrase));
    }

    public List<Alias> list(AliasKind kind) {
        return db.query("SELECT * FROM aliases WHERE kind=? ORDER BY phrase", AliasRepository::map, kind.name());
    }

    public List<Alias> listAll() {
        return db.query("SELECT * FROM aliases ORDER BY kind, phrase", AliasRepository::map);
    }

    /** Inserts or replaces. Callers must ask the user before replacing; see {@link LearningService}. */
    public void put(AliasKind kind, String phrase, String target) {
        String p = key(phrase);
        if (p.isEmpty() || p.length() > MAX_PHRASE) throw new IllegalArgumentException("Alias phrase must be 1-" + MAX_PHRASE + " characters");
        if (target == null || target.isBlank() || target.length() > MAX_TARGET) throw new IllegalArgumentException("Alias target is invalid");
        db.update("INSERT INTO aliases(kind,phrase,target) VALUES(?,?,?) ON CONFLICT(kind,phrase) DO UPDATE SET target=excluded.target, disabled=0",
                kind.name(), p, target.strip());
    }

    public void recordUse(long id) {
        db.update("UPDATE aliases SET usage_count=usage_count+1, last_used=datetime('now') WHERE id=?", id);
    }

    public void setDisabled(long id, boolean disabled) { db.update("UPDATE aliases SET disabled=? WHERE id=?", disabled ? 1 : 0, id); }

    public void delete(long id) { db.update("DELETE FROM aliases WHERE id=?", id); }

    public void deleteAll() { db.update("DELETE FROM aliases"); }
}
