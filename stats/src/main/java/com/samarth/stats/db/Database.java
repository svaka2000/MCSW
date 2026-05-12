package com.samarth.stats.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * SQLite connection holder with WAL mode and a tiny migration scheme.
 * Single connection — SQLite serializes writes anyway, and HikariCP is overkill here.
 * Callers are responsible for thread-safety; we synchronize on the connection for writes.
 */
public final class Database {

    private final Connection connection;
    private final Logger logger;

    public Database(File file, Logger logger) throws SQLException {
        this.logger = logger;
        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not loaded (check plugin.yml libraries)", e);
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
        migrate();
    }

    public Connection connection() { return connection; }

    public void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }

    private void migrate() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY)");
            int current = 0;
            try (ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
                if (rs.next()) current = rs.getInt(1);
            }
            if (current < 1) {
                applyV1(s);
                s.execute("INSERT INTO schema_version (version) VALUES (1)");
                logger.info("Migrated stats DB to v1");
            }
        }
    }

    private void applyV1(Statement s) throws SQLException {
        s.execute(
            "CREATE TABLE players (" +
            "  uuid TEXT PRIMARY KEY, " +
            "  name TEXT NOT NULL, " +
            "  first_seen INTEGER NOT NULL, " +
            "  last_seen INTEGER NOT NULL" +
            ")"
        );
        s.execute(
            "CREATE TABLE duel_results (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "  ts INTEGER NOT NULL, " +
            "  winner_uuid TEXT NOT NULL, " +
            "  loser_uuid TEXT NOT NULL, " +
            "  kit TEXT NOT NULL, " +
            "  best_of INTEGER NOT NULL, " +
            "  winner_rounds INTEGER NOT NULL, " +
            "  loser_rounds INTEGER NOT NULL, " +
            "  duration_seconds INTEGER NOT NULL DEFAULT 0" +
            ")"
        );
        s.execute(
            "CREATE TABLE tournament_results (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "  ts INTEGER NOT NULL, " +
            "  tournament_id TEXT NOT NULL, " +
            "  player_uuid TEXT NOT NULL, " +
            "  rounds_won INTEGER NOT NULL, " +
            "  total_rounds INTEGER NOT NULL" +
            ")"
        );
        s.execute("CREATE INDEX idx_duel_winner ON duel_results(winner_uuid)");
        s.execute("CREATE INDEX idx_duel_loser ON duel_results(loser_uuid)");
        s.execute("CREATE INDEX idx_duel_kit ON duel_results(kit)");
        s.execute("CREATE INDEX idx_tournament_player ON tournament_results(player_uuid)");
    }
}
