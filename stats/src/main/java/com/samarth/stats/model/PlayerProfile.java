package com.samarth.stats.model;

import java.util.Map;
import java.util.UUID;

public record PlayerProfile(
    UUID uuid,
    String name,
    long firstSeenMillis,
    long lastSeenMillis,
    int duelWins,
    int duelLosses,
    int tournamentWins,         // tournaments where roundsWon == totalRounds (champion)
    int tournamentEntries,      // total tournaments played
    Map<String, KitStats> perKitStats
) {
    public int totalDuels() { return duelWins + duelLosses; }
    public double winRate() {
        int total = totalDuels();
        return total == 0 ? 0.0 : (double) duelWins / total;
    }
}
