package com.samarth.stats.model;

import java.util.UUID;

/** A single completed duel — append-only, recorded at match end. */
public record DuelResult(
    long timestampMillis,
    UUID winnerUuid,
    UUID loserUuid,
    String kit,
    int bestOf,
    int winnerRounds,
    int loserRounds,
    long durationSeconds
) {}
