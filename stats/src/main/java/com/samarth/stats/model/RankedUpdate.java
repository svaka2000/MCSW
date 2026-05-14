package com.samarth.stats.model;

import java.util.UUID;

/** Delta + new Elo for one player after a ranked match settles. */
public record RankedUpdate(
    UUID uuid,
    String kit,
    int oldElo,
    int newElo,
    int wins,
    int losses
) {
    public int delta() { return newElo - oldElo; }
}
