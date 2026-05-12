package com.samarth.tourney.tournament;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public final class Tournament {
    private final UUID id = UUID.randomUUID();
    private TournamentState state = TournamentState.NONE;
    private final List<UUID> players = new ArrayList<>();
    @Nullable private Bracket bracket;

    /** arena name -> active match running in that arena */
    private final Map<String, Match> activeMatchesByArena = new HashMap<>();
    /** player UUID -> their current match */
    private final Map<UUID, Match> activeMatchesByPlayer = new HashMap<>();

    private long joinDeadlineMillis;

    // Per-tournament settings, set when /tourney start runs (so each tournament can override config).
    private int joinWindowSeconds;
    private int killsToWin;
    private int freezeSeconds;
    private int matchTimeCapSeconds;
    /** Name of the PvPTLKits kit used for this tournament. Required — set in /tourney start. */
    private String kitName = "";

    public int joinWindowSeconds() { return joinWindowSeconds; }
    public void setJoinWindowSeconds(int v) { this.joinWindowSeconds = v; }
    public int killsToWin() { return killsToWin; }
    public void setKillsToWin(int v) { this.killsToWin = v; }
    public int freezeSeconds() { return freezeSeconds; }
    public void setFreezeSeconds(int v) { this.freezeSeconds = v; }
    public int matchTimeCapSeconds() { return matchTimeCapSeconds; }
    public void setMatchTimeCapSeconds(int v) { this.matchTimeCapSeconds = v; }
    public String kitName() { return kitName; }
    public void setKitName(String v) { this.kitName = v == null ? "" : v; }

    public UUID id() { return id; }

    public TournamentState state() { return state; }
    public void setState(TournamentState state) { this.state = state; }

    public List<UUID> players() { return players; }

    public @Nullable Bracket bracket() { return bracket; }
    public void setBracket(Bracket bracket) { this.bracket = bracket; }

    public Map<String, Match> activeMatchesByArena() { return activeMatchesByArena; }
    public Map<UUID, Match> activeMatchesByPlayer() { return activeMatchesByPlayer; }

    public long joinDeadlineMillis() { return joinDeadlineMillis; }
    public void setJoinDeadlineMillis(long joinDeadlineMillis) { this.joinDeadlineMillis = joinDeadlineMillis; }
}
