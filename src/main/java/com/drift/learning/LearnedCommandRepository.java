package com.drift.learning;

import com.drift.persistence.Database;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class LearnedCommandRepository {
    public record LearnedCommand(long id, String utterance, String actionId, String paramsJson, int successCount,
                                 int rejectCount, boolean disabled, String lastUsed) {}

    private final Database db;

    public LearnedCommandRepository(Database db) { this.db = db; }

    private static LearnedCommand map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LearnedCommand(rs.getLong("id"), rs.getString("utterance"), rs.getString("action_id"), rs.getString("params"),
                rs.getInt("success_count"), rs.getInt("reject_count"), rs.getInt("disabled") != 0, rs.getString("last_used"));
    }

    public Optional<LearnedCommand> find(String utterance) {
        return db.queryOne("SELECT * FROM learned_commands WHERE utterance=?", LearnedCommandRepository::map, utterance);
    }

    public List<LearnedCommand> list() { return db.query("SELECT * FROM learned_commands ORDER BY utterance", LearnedCommandRepository::map); }

    public void recordSuccess(String utterance, String actionId, String paramsJson) {
        db.update("INSERT INTO learned_commands(utterance,action_id,params,success_count,last_used) VALUES(?,?,?,1,datetime('now')) "
                + "ON CONFLICT(utterance) DO UPDATE SET action_id=excluded.action_id, params=excluded.params, "
                + "success_count=success_count+1, last_used=datetime('now')", utterance, actionId, paramsJson);
    }

    public void recordReject(String utterance) {
        db.update("UPDATE learned_commands SET reject_count=reject_count+1, disabled=1 WHERE utterance=?", utterance);
    }

    public void delete(long id) { db.update("DELETE FROM learned_commands WHERE id=?", id); }

    public void deleteAll() { db.update("DELETE FROM learned_commands"); }
}
