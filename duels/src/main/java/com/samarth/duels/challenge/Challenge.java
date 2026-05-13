package com.samarth.duels.challenge;

import java.util.UUID;

/**
 * Pending duel challenge. For 1v1 challenges, {@code challenger} and {@code target} are the two
 * fighters. For party challenges, they are the two party leaders — actual teams are resolved
 * from their parties when the target hits accept.
 */
public record Challenge(
    UUID challenger,
    UUID target,
    String kitName,
    int rounds,
    boolean useTimeLimit,
    long expiresAtMillis,
    boolean party
) {
    /** Backwards-compatible constructor for 1v1 challenges. */
    public Challenge(UUID challenger, UUID target, String kitName,
                     int rounds, boolean useTimeLimit, long expiresAtMillis) {
        this(challenger, target, kitName, rounds, useTimeLimit, expiresAtMillis, false);
    }

    public boolean expired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }
}
