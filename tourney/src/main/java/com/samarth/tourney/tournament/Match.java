package com.samarth.tourney.tournament;

import java.util.UUID;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

public final class Match {
    private final UUID id = UUID.randomUUID();
    private final BracketMatch bracketMatch;
    private final Arena arena;
    private final UUID playerA;
    private final UUID playerB;

    private MatchState state = MatchState.PENDING;
    private int killsA;
    private int killsB;
    private long startMillis;

    @Nullable private BukkitTask freezeTask;
    @Nullable private BukkitTask timerTask;
    @Nullable private BukkitTask actionBarTask;
    @Nullable private UUID disconnectedPlayer;
    @Nullable private Scoreboard sidebarScoreboard;

    public Match(BracketMatch bracketMatch, Arena arena, UUID playerA, UUID playerB) {
        this.bracketMatch = bracketMatch;
        this.arena = arena;
        this.playerA = playerA;
        this.playerB = playerB;
    }

    public UUID id() { return id; }
    public BracketMatch bracketMatch() { return bracketMatch; }
    public Arena arena() { return arena; }
    public UUID playerA() { return playerA; }
    public UUID playerB() { return playerB; }

    public MatchState state() { return state; }
    public void setState(MatchState state) { this.state = state; }

    public int killsA() { return killsA; }
    public int killsB() { return killsB; }
    public void incrementKillsA() { killsA++; }
    public void incrementKillsB() { killsB++; }

    public long startMillis() { return startMillis; }
    public void setStartMillis(long startMillis) { this.startMillis = startMillis; }

    public @Nullable BukkitTask freezeTask() { return freezeTask; }
    public void setFreezeTask(@Nullable BukkitTask freezeTask) { this.freezeTask = freezeTask; }
    public @Nullable BukkitTask timerTask() { return timerTask; }
    public void setTimerTask(@Nullable BukkitTask timerTask) { this.timerTask = timerTask; }
    public @Nullable BukkitTask actionBarTask() { return actionBarTask; }
    public void setActionBarTask(@Nullable BukkitTask actionBarTask) { this.actionBarTask = actionBarTask; }

    public @Nullable UUID disconnectedPlayer() { return disconnectedPlayer; }
    public void setDisconnectedPlayer(@Nullable UUID disconnectedPlayer) { this.disconnectedPlayer = disconnectedPlayer; }

    public @Nullable Scoreboard sidebarScoreboard() { return sidebarScoreboard; }
    public void setSidebarScoreboard(@Nullable Scoreboard sidebarScoreboard) { this.sidebarScoreboard = sidebarScoreboard; }

    public boolean involves(UUID id) {
        return playerA.equals(id) || playerB.equals(id);
    }

    public UUID opponentOf(UUID id) {
        if (playerA.equals(id)) return playerB;
        if (playerB.equals(id)) return playerA;
        throw new IllegalArgumentException("Not in this match: " + id);
    }
}
