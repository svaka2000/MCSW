package com.samarth.stats;

import com.samarth.stats.model.DuelResult;
import com.samarth.stats.model.EloEntry;
import com.samarth.stats.model.LeaderboardEntry;
import com.samarth.stats.model.PlayerProfile;
import com.samarth.stats.model.RankedUpdate;
import com.samarth.stats.model.TournamentResult;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * Public API exposed via Bukkit's {@link org.bukkit.plugin.ServicesManager} for other
 * PvPTL plugins (Tourney, Duels) to push gameplay events into a shared SQLite store.
 *
 * Soft-depend on the {@code PvPTLStats} plugin; gracefully no-op if not loaded:
 *
 * <pre>
 *   var rsp = Bukkit.getServicesManager().getRegistration(StatsService.class);
 *   StatsService stats = rsp != null ? rsp.getProvider() : null;
 *   if (stats != null) stats.recordDuelResult(...);
 * </pre>
 *
 * All record* methods are fire-and-forget and run async. Read methods return
 * synchronously and may block briefly on the SQLite read — callers running on
 * the main thread should wrap them in {@code runTaskAsynchronously} for big queries.
 */
public interface StatsService {

    /** Update or create the player row. Idempotent. Async. */
    void touchPlayer(UUID uuid, String name);

    /** Record a completed duel. Async. */
    void recordDuelResult(DuelResult result);

    /** Record one player's outcome from a finished tournament. Async. */
    void recordTournamentResult(TournamentResult result);

    /** Aggregated profile for one player, or null if no records exist. May block on a small SQLite read. */
    @Nullable PlayerProfile getProfile(UUID uuid);

    /** Top players by total duel wins, descending. */
    List<LeaderboardEntry> topDuelWins(int limit);

    /** Top players by total tournament championships, descending. */
    List<LeaderboardEntry> topTournamentWins(int limit);

    /** Top players by duel K/D-ish ratio (wins / max(losses,1)), descending. Minimum 5 duels to qualify. */
    List<LeaderboardEntry> topWinRate(int limit);

    /** Top players for a specific kit by wins on that kit. */
    List<LeaderboardEntry> topByKit(String kit, int limit);

    /**
     * Record a ranked duel — writes the duel row, applies the Elo formula to both
     * players' rows for this kit, and fires {@code onComplete} with the resulting
     * deltas on the main thread (callback may be null).
     */
    void recordRankedDuelResult(DuelResult result, @Nullable BiConsumer<RankedUpdate, RankedUpdate> onComplete);

    /** Current Elo for this player on this kit, or the configured starting Elo if no row. */
    int getElo(UUID uuid, String kit);

    /** Full Elo entry for this player on this kit, or null if no row exists yet. */
    @Nullable EloEntry getEloEntry(UUID uuid, String kit);

    /** Top players by Elo on a specific kit, descending. */
    List<EloEntry> topElo(String kit, int limit);

    /** Quick "is the backend healthy?" check. */
    boolean isReady();
}
