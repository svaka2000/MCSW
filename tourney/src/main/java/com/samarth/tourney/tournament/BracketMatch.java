package com.samarth.tourney.tournament;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * One node in the bracket tree. Players fill in as previous rounds resolve.
 * A bye is represented by playerA != null and playerB == null (or vice versa) — auto-advances.
 */
public final class BracketMatch {
    private final int round;
    private final int index;
    @Nullable private UUID playerA;
    @Nullable private UUID playerB;
    @Nullable private UUID winner;
    private boolean played;
    private boolean started;

    public BracketMatch(int round, int index, @Nullable UUID playerA, @Nullable UUID playerB) {
        this.round = round;
        this.index = index;
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public int round() { return round; }
    public int index() { return index; }

    public @Nullable UUID playerA() { return playerA; }
    public @Nullable UUID playerB() { return playerB; }
    public @Nullable UUID winner() { return winner; }

    public void setPlayerA(@Nullable UUID id) { this.playerA = id; }
    public void setPlayerB(@Nullable UUID id) { this.playerB = id; }
    public void setWinner(@Nullable UUID id) { this.winner = id; }

    public boolean played() { return played; }
    public void setPlayed(boolean played) { this.played = played; }

    public boolean started() { return started; }
    public void setStarted(boolean started) { this.started = started; }

    public boolean readyToPlay() {
        return !played && !started && playerA != null && playerB != null;
    }

    public boolean isBye() {
        return !played && (
            (playerA != null && playerB == null) ||
            (playerB != null && playerA == null)
        );
    }
}
