package com.samarth.duels.challenge;

import java.util.UUID;

public record Challenge(
    UUID challenger,
    UUID target,
    String kitName,
    int rounds,
    boolean useTimeLimit,
    long expiresAtMillis
) {
    public boolean expired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }
}
