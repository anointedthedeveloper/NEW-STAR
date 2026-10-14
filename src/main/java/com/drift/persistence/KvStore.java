package com.drift.persistence;

import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Small string preferences that don't warrant a settings field (first-run flag, widget layer flags...). */
@Repository
public class KvStore {
    private final Database db;

    public KvStore(Database db) { this.db = db; }

    public Optional<String> get(String key) { return db.queryOne("SELECT value FROM kv WHERE key=?", rs -> rs.getString(1), key); }

    public boolean getBool(String key, boolean fallback) { return get(key).map(Boolean::parseBoolean).orElse(fallback); }

    public void put(String key, String value) {
        db.update("INSERT INTO kv(key,value,updated_at) VALUES(?,?,datetime('now')) ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at", key, value);
    }

    public void putBool(String key, boolean v) { put(key, Boolean.toString(v)); }
}
