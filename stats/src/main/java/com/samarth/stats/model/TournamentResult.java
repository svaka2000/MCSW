package com.samarth.stats.model;

import java.util.UUID;

/**
 * One row per player per tournament — appended when the bracket completes.
 * `roundsWon` is the number of bracket rounds this player won (including bye-resolutions).
 * Champion has roundsWon == totalRounds.
 */
public record TournamentResult(
    long timestampMillis,
    UUID tournamentId,
    UUID playerUuid,
    int roundsWon,
    int totalRoundsInBracket
) {}
