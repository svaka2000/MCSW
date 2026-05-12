package com.samarth.stats;

import com.samarth.stats.db.Database;
import com.samarth.stats.model.DuelResult;
import com.samarth.stats.model.KitStats;
import com.samarth.stats.model.LeaderboardEntry;
import com.samarth.stats.model.PlayerProfile;
import com.samarth.stats.model.TournamentResult;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class StatsServiceImpl implements StatsService {
    private final JavaPlugin plugin;
    private final Database db;

    public StatsServiceImpl(JavaPlugin plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    @Override
    public boolean isReady() { return db != null; }

    @Override
    public void touchPlayer(UUID uuid, String name) {
        runAsync(() -> {
            long now = System.currentTimeMillis();
            String sql =
                "INSERT INTO players (uuid, name, first_seen, last_seen) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, now);
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                warn("touchPlayer", e);
            }
        });
    }

    @Override
    public void recordDuelResult(DuelResult r) {
        runAsync(() -> {
            String sql =
                "INSERT INTO duel_results (ts, winner_uuid, loser_uuid, kit, best_of, " +
                "winner_rounds, loser_rounds, duration_seconds) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setLong(1, r.timestampMillis());
                ps.setString(2, r.winnerUuid().toString());
                ps.setString(3, r.loserUuid().toString());
                ps.setString(4, r.kit());
                ps.setInt(5, r.bestOf());
                ps.setInt(6, r.winnerRounds());
                ps.setInt(7, r.loserRounds());
                ps.setLong(8, r.durationSeconds());
                ps.executeUpdate();
            } catch (SQLException e) {
                warn("recordDuelResult", e);
            }
        });
    }

    @Override
    public void recordTournamentResult(TournamentResult r) {
        runAsync(() -> {
            String sql =
                "INSERT INTO tournament_results (ts, tournament_id, player_uuid, rounds_won, total_rounds) " +
                "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
                ps.setLong(1, r.timestampMillis());
                ps.setString(2, r.tournamentId().toString());
                ps.setString(3, r.playerUuid().toString());
                ps.setInt(4, r.roundsWon());
                ps.setInt(5, r.totalRoundsInBracket());
                ps.executeUpdate();
            } catch (SQLException e) {
                warn("recordTournamentResult", e);
            }
        });
    }

    @Override
    public @Nullable PlayerProfile getProfile(UUID uuid) {
        String name;
        long firstSeen, lastSeen;
        int duelWins = 0, duelLosses = 0, tournamentEntries = 0, tournamentWins = 0;
        Map<String, KitStats> perKit = new HashMap<>();

        try {
            try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT name, first_seen, last_seen FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    name = rs.getString("name");
                    firstSeen = rs.getLong("first_seen");
                    lastSeen = rs.getLong("last_seen");
                }
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM duel_results WHERE winner_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) duelWins = rs.getInt(1); }
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM duel_results WHERE loser_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) duelLosses = rs.getInt(1); }
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM tournament_results WHERE player_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) tournamentEntries = rs.getInt(1); }
            }
            try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT COUNT(*) FROM tournament_results WHERE player_uuid = ? AND rounds_won = total_rounds")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) tournamentWins = rs.getInt(1); }
            }
            // Per-kit stats
            try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT kit, " +
                "  SUM(CASE WHEN winner_uuid = ? THEN 1 ELSE 0 END) AS wins, " +
                "  SUM(CASE WHEN loser_uuid = ? THEN 1 ELSE 0 END) AS losses " +
                "FROM duel_results WHERE winner_uuid = ? OR loser_uuid = ? GROUP BY kit")) {
                String u = uuid.toString();
                ps.setString(1, u); ps.setString(2, u); ps.setString(3, u); ps.setString(4, u);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String kit = rs.getString("kit");
                        perKit.put(kit, new KitStats(kit, rs.getInt("wins"), rs.getInt("losses")));
                    }
                }
            }
        } catch (SQLException e) {
            warn("getProfile", e);
            return null;
        }

        return new PlayerProfile(uuid, name, firstSeen, lastSeen,
            duelWins, duelLosses, tournamentWins, tournamentEntries, perKit);
    }

    @Override
    public List<LeaderboardEntry> topDuelWins(int limit) {
        return runLeaderboardQuery(
            "SELECT winner_uuid AS uuid, COUNT(*) AS score FROM duel_results " +
            "GROUP BY winner_uuid ORDER BY score DESC LIMIT ?",
            limit);
    }

    @Override
    public List<LeaderboardEntry> topTournamentWins(int limit) {
        return runLeaderboardQuery(
            "SELECT player_uuid AS uuid, COUNT(*) AS score FROM tournament_results " +
            "WHERE rounds_won = total_rounds GROUP BY player_uuid ORDER BY score DESC LIMIT ?",
            limit);
    }

    @Override
    public List<LeaderboardEntry> topWinRate(int limit) {
        // Min 5 duels to qualify; tiebreak by total wins
        String sql =
            "WITH wins AS (SELECT winner_uuid AS uuid, COUNT(*) AS w FROM duel_results GROUP BY winner_uuid), " +
            "losses AS (SELECT loser_uuid AS uuid, COUNT(*) AS l FROM duel_results GROUP BY loser_uuid) " +
            "SELECT COALESCE(w.uuid, l.uuid) AS uuid, " +
            "  CAST(COALESCE(w.w, 0) AS REAL) / (COALESCE(w.w, 0) + COALESCE(l.l, 0)) AS score " +
            "FROM wins w LEFT JOIN losses l ON w.uuid = l.uuid " +
            "WHERE (COALESCE(w.w, 0) + COALESCE(l.l, 0)) >= 5 " +
            "ORDER BY score DESC, w.w DESC LIMIT ?";
        return runLeaderboardQuery(sql, limit);
    }

    @Override
    public List<LeaderboardEntry> topByKit(String kit, int limit) {
        String sql =
            "SELECT winner_uuid AS uuid, COUNT(*) AS score FROM duel_results " +
            "WHERE kit = ? GROUP BY winner_uuid ORDER BY score DESC LIMIT ?";
        List<LeaderboardEntry> out = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, kit);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    rank++;
                    UUID id = UUID.fromString(rs.getString("uuid"));
                    out.add(new LeaderboardEntry(rank, id, nameOf(id), rs.getDouble("score")));
                }
            }
        } catch (SQLException e) {
            warn("topByKit", e);
        }
        return out;
    }

    private List<LeaderboardEntry> runLeaderboardQuery(String sql, int limit) {
        List<LeaderboardEntry> out = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                int rank = 0;
                while (rs.next()) {
                    rank++;
                    UUID id = UUID.fromString(rs.getString("uuid"));
                    out.add(new LeaderboardEntry(rank, id, nameOf(id), rs.getDouble("score")));
                }
            }
        } catch (SQLException e) {
            warn("leaderboard", e);
        }
        return out;
    }

    private String nameOf(UUID id) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String n = op.getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }

    private void runAsync(Runnable r) {
        if (!plugin.isEnabled()) {
            r.run();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    private void warn(String op, Exception e) {
        plugin.getLogger().warning("[stats] " + op + ": " + e.getMessage());
    }
}
