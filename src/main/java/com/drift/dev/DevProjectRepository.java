package com.drift.dev;

import com.drift.persistence.Database;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class DevProjectRepository {
    public record Project(String path, String name, String lastOpened) {}

    private final Database db;

    public DevProjectRepository(Database db) { this.db = db; }

    private static Project map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Project(rs.getString("path"), rs.getString("name"), rs.getString("last_opened"));
    }

    public void upsert(Path dir) {
        String name = dir.getFileName() == null ? dir.toString() : dir.getFileName().toString();
        db.update("INSERT INTO dev_projects(path,name) VALUES(?,?) ON CONFLICT(path) DO NOTHING", dir.toString(), name);
    }

    public void markOpened(Path dir) {
        upsert(dir);
        db.update("UPDATE dev_projects SET last_opened=datetime('now') WHERE path=?", dir.toString());
    }

    public void remove(String path) { db.update("DELETE FROM dev_projects WHERE path=?", path); }

    public List<Project> list() { return db.query("SELECT * FROM dev_projects ORDER BY COALESCE(last_opened,'') DESC, name", DevProjectRepository::map); }

    public Optional<Project> mostRecent() {
        return db.queryOne("SELECT * FROM dev_projects WHERE last_opened IS NOT NULL ORDER BY last_opened DESC LIMIT 1", DevProjectRepository::map);
    }

    /** Most recent project last opened on the previous local calendar day. */
    public Optional<Project> openedYesterday() {
        return db.queryOne("SELECT * FROM dev_projects WHERE date(last_opened,'localtime') = date('now','localtime','-1 day') "
                + "ORDER BY last_opened DESC LIMIT 1", DevProjectRepository::map);
    }

    public List<Project> matching(String spoken) {
        String q = spoken.toLowerCase(Locale.ROOT).strip();
        return list().stream().filter(p -> {
            String n = p.name().toLowerCase(Locale.ROOT);
            return n.equals(q) || n.replace('-', ' ').replace('_', ' ').contains(q) || n.contains(q);
        }).toList();
    }
}
