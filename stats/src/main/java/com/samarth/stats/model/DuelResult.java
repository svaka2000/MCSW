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
    long durationSeconds,
    boolean ranked
) {
    /** Back-compat constructor — unranked by default. */
    public DuelResult(long timestampMillis, UUID winnerUuid, UUID loserUuid, String kit,
                      int bestOf, int winnerRounds, int loserRounds, long durationSeconds) {
        this(timestampMillis, winnerUuid, loserUuid, kit, bestOf,
            winnerRounds, loserRounds, durationSeconds, false);
    }
}
