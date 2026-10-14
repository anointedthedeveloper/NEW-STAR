package com.drift.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies classpath resources db/migration/V{n}__name.sql in order; each runs in one transaction. */
class Migrator {
    static final int LATEST = 3;
    private static final Pattern VERSION_TAG = Pattern.compile("^V(\\d+)__.*");
    private final Database db;

    Migrator(Database db) { this.db = db; }

    void migrate() {
        try (Connection c = db.connect()) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at TEXT NOT NULL)");
            }
            Set<Integer> applied = new HashSet<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT version FROM schema_migrations")) {
                while (rs.next()) applied.add(rs.getInt(1));
            }
            for (String name : MigrationIndex.NAMES) {
                Matcher m = VERSION_TAG.matcher(name);
                if (!m.matches()) throw new IllegalStateException("Bad migration name " + name);
                int version = Integer.parseInt(m.group(1));
                if (applied.contains(version)) continue;
                apply(c, version, name);
            }
        } catch (SQLException e) {
            throw new Database.PersistenceException("Database migration failed: " + e.getMessage(), e);
        }
    }

    private void apply(Connection c, int version, String name) throws SQLException {
        String sql = read("/db/migration/" + name + ".sql");
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try (Statement s = c.createStatement()) {
            for (String stmt : sql.split(";\\s*(\\r?\\n|$)")) {
                String t = stmt.strip();
                if (!t.isEmpty()) s.execute(t);
            }
            try (var ps = c.prepareStatement("INSERT INTO schema_migrations(version,name,applied_at) VALUES(?,?,datetime('now'))")) {
                ps.setInt(1, version);
                ps.setString(2, name);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }

    private static String read(String resource) {
        try (InputStream in = Migrator.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing migration resource " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class MigrationIndex {
        static final String[] NAMES = {"V1__init", "V2__learning", "V3__apps_and_projects"};
        private MigrationIndex() {}
    }
}
