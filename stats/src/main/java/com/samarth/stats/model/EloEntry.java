package com.samarth.stats.model;

import java.util.UUID;

/** Snapshot of one player's Elo on one kit. */
public record EloEntry(
    UUID uuid,
    String name,
    String kit,
    int elo,
    int wins,
    int losses,
    long lastUpdatedMillis
) {
    public int total() { return wins + losses; }
    public double winRate() {
        int t = total();
        return t == 0 ? 0.0 : (double) wins / t;
    }
}
