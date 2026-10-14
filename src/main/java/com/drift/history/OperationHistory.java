package com.drift.history;

import com.drift.config.SettingsService;
import com.drift.persistence.Database;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Local audit trail of every action Drift performed or refused. Retention is enforced by {@link #purgeExpired()}. */
@Repository
public class OperationHistory {
    public record Entry(long id, String ts, String utterance, String actionId, String params, String status, String message, long durationMs) {}

    private final Database db;
    private final SettingsService settings;

    public OperationHistory(Database db, SettingsService settings) {
        this.db = db;
        this.settings = settings;
    }

    public void record(String utterance, String actionId, String params, String status, String message, long durationMs) {
        String u = settings.get().privacy().storeTranscripts() ? Redactor.redact(utterance) : null;
        db.update("INSERT INTO operation_history(utterance,action_id,params,status,message,duration_ms) VALUES(?,?,?,?,?,?)",
                u, actionId, Redactor.redact(params), status, Redactor.redact(message), durationMs);
    }

    public List<Entry> recent(int limit) {
        return db.query("SELECT * FROM operation_history ORDER BY id DESC LIMIT ?",
                rs -> new Entry(rs.getLong("id"), rs.getString("ts"), rs.getString("utterance"), rs.getString("action_id"),
                        rs.getString("params"), rs.getString("status"), rs.getString("message"), rs.getLong("duration_ms")), limit);
    }

    public int purgeExpired() {
        return db.update("DELETE FROM operation_history WHERE ts < datetime('now', ?)", "-" + settings.get().privacy().historyRetentionDays() + " days");
    }

    public void clear() { db.update("DELETE FROM operation_history"); }
}
