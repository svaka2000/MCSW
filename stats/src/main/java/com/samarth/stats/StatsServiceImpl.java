package com.samarth.stats;

import com.samarth.stats.db.Database;
import com.samarth.stats.model.DuelResult;
import com.samarth.stats.model.EloEntry;
import com.samarth.stats.model.KitStats;
import com.samarth.stats.model.LeaderboardEntry;
import com.samarth.stats.model.PlayerProfile;
import com.samarth.stats.model.RankedUpdate;
import com.samarth.stats.model.TournamentResult;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
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
        runAsync(() -> insertDuelRow(r));
    }

    private void insertDuelRow(DuelResult r) {
        String sql =
            "INSERT INTO duel_results (ts, winner_uuid, loser_uuid, kit, best_of, " +
            "winner_rounds, loser_rounds, duration_seconds, ranked) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, r.timestampMillis());
            ps.setString(2, r.winnerUuid().toString());
            ps.setString(3, r.loserUuid().toString());
            ps.setString(4, r.kit());
            ps.setInt(5, r.bestOf());
            ps.setInt(6, r.winnerRounds());
            ps.setInt(7, r.loserRounds());
            ps.setLong(8, r.durationSeconds());
            ps.setInt(9, r.ranked() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            warn("recordDuelResult", e);
        }
    }

    @Override
    public void recordRankedDuelResult(DuelResult result, @Nullable BiConsumer<RankedUpdate, RankedUpdate> onComplete) {
        // Force ranked=true even if caller passed false.
        DuelResult ranked = result.ranked() ? result : new DuelResult(
            result.timestampMillis(), result.winnerUuid(), result.loserUuid(),
            result.kit(), result.bestOf(), result.winnerRounds(), result.loserRounds(),
            result.durationSeconds(), true);
        runAsync(() -> {
            insertDuelRow(ranked);
            RankedUpdate[] updates;
            try {
                updates = applyEloUpdate(ranked.winnerUuid(), ranked.loserUuid(), ranked.kit());
            } catch (SQLException e) {
                warn("recordRankedDuelResult", e);
                return;
            }
            if (onComplete != null && updates != null) {
                final RankedUpdate w = updates[0];
                final RankedUpdate l = updates[1];
                Bukkit.getScheduler().runTask(plugin, () -> onComplete.accept(w, l));
            }
        });
    }

    /** Standard Elo: K=32 default, configurable via duels.elo-k-factor (read from the duels plugin). */
    private RankedUpdate[] applyEloUpdate(UUID winnerId, UUID loserId, String kit) throws SQLException {
        int kFactor = readK();
        int starting = readStartingElo();
        EloEntry winnerOld = upsertEloRow(winnerId, kit, starting);
        EloEntry loserOld = upsertEloRow(loserId, kit, starting);
        int rw = winnerOld.elo();
        int rl = loserOld.elo();
        double ew = 1.0 / (1.0 + Math.pow(10.0, (rl - rw) / 400.0));
        double el = 1.0 - ew;
        int rwNew = (int) Math.round(rw + kFactor * (1.0 - ew));
        int rlNew = (int) Math.round(rl + kFactor * (0.0 - el));
        long now = System.currentTimeMillis();
        writeEloRow(winnerId, kit, rwNew, winnerOld.wins() + 1, winnerOld.losses(), now);
        writeEloRow(loserId, kit, rlNew, loserOld.wins(), loserOld.losses() + 1, now);
        RankedUpdate w = new RankedUpdate(winnerId, kit, rw, rwNew,
            winnerOld.wins() + 1, winnerOld.losses());
        RankedUpdate l = new RankedUpdate(loserId, kit, rl, rlNew,
            loserOld.wins(), loserOld.losses() + 1);
        return new RankedUpdate[]{w, l};
    }

    /** Read-or-create the elo row, returning the BEFORE-update snapshot. */
    private EloEntry upsertEloRow(UUID uuid, String kit, int starting) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(
            "SELECT elo, wins, losses, last_updated FROM duel_elo WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EloEntry(uuid, nameOf(uuid), kit,
                        rs.getInt("elo"), rs.getInt("wins"), rs.getInt("losses"),
                        rs.getLong("last_updated"));
                }
            }
        }
        try (PreparedStatement ps = db.connection().prepareStatement(
            "INSERT INTO duel_elo (uuid, kit, elo, wins, losses, last_updated) VALUES (?, ?, ?, 0, 0, 0)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            ps.setInt(3, starting);
            ps.executeUpdate();
        }
        return new EloEntry(uuid, nameOf(uuid), kit, starting, 0, 0, 0L);
    }

    private void writeEloRow(UUID uuid, String kit, int elo, int wins, int losses, long ts) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement(
            "UPDATE duel_elo SET elo = ?, wins = ?, losses = ?, last_updated = ? " +
            "WHERE uuid = ? AND kit = ?")) {
            ps.setInt(1, elo);
            ps.setInt(2, wins);
            ps.setInt(3, losses);
            ps.setLong(4, ts);
            ps.setString(5, uuid.toString());
            ps.setString(6, kit);
            ps.executeUpdate();
        }
    }

    @Override
    public int getElo(UUID uuid, String kit) {
        int starting = readStartingElo();
        try (PreparedStatement ps = db.connection().prepareStatement(
            "SELECT elo FROM duel_elo WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            warn("getElo", e);
        }
        return starting;
    }

    @Override
    public @Nullable EloEntry getEloEntry(UUID uuid, String kit) {
        try (PreparedStatement ps = db.connection().prepareStatement(
            "SELECT elo, wins, losses, last_updated FROM duel_elo WHERE uuid = ? AND kit = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, kit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EloEntry(uuid, nameOf(uuid), kit,
                        rs.getInt("elo"), rs.getInt("wins"), rs.getInt("losses"),
                        rs.getLong("last_updated"));
                }
            }
        } catch (SQLException e) {
            warn("getEloEntry", e);
        }
        return null;
    }

    @Override
    public List<EloEntry> topElo(String kit, int limit) {
        List<EloEntry> out = new ArrayList<>();
        String sql = "SELECT uuid, elo, wins, losses, last_updated FROM duel_elo " +
                     "WHERE kit = ? ORDER BY elo DESC, wins DESC LIMIT ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, kit);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("uuid"));
                    out.add(new EloEntry(id, nameOf(id), kit,
                        rs.getInt("elo"), rs.getInt("wins"), rs.getInt("losses"),
                        rs.getLong("last_updated")));
                }
            }
        } catch (SQLException e) {
            warn("topElo", e);
        }
        return out;
    }

    private int readK() {
        int k = plugin.getConfig().getInt("elo.k-factor", 32);
        // Allow the duels plugin's config to win if it exposes the value.
        var duels = Bukkit.getPluginManager().getPlugin("PvPTLDuels");
        if (duels != null) k = duels.getConfig().getInt("duels.elo-k-factor", k);
        return k;
    }

    private int readStartingElo() {
        int s = plugin.getConfig().getInt("elo.starting", 1000);
        var duels = Bukkit.getPluginManager().getPlugin("PvPTLDuels");
        if (duels != null) s = duels.getConfig().getInt("duels.elo-starting", s);
        return s;
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
