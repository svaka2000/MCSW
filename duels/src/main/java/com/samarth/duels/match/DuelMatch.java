package com.samarth.duels.match;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

public final class DuelMatch {
    private final UUID id = UUID.randomUUID();
    private final UUID playerA;
    private final UUID playerB;
    private final String kitName;
    private final int bestOf;
    private final boolean useTimeLimit;
    @Nullable private Scoreboard sidebarScoreboard;

    private MatchState state = MatchState.PENDING;
    private int killsA;
    private int killsB;
    private int roundsA;
    private int roundsB;

    @Nullable private BukkitTask freezeTask;
    @Nullable private BukkitTask timerTask;
    @Nullable private BukkitTask actionBarTask;

    // In-memory inventory snapshot (cleared at match end). Crash = lose inventory; acceptable for duels.
    private final Map<UUID, ItemStack[]> savedInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, ItemStack> savedOffhand = new HashMap<>();
    private final Map<UUID, Location> savedLocation = new HashMap<>();
    private final Map<UUID, GameMode> savedGameMode = new HashMap<>();

    public DuelMatch(UUID playerA, UUID playerB, String kitName, int bestOf, boolean useTimeLimit) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.kitName = kitName;
        this.bestOf = Math.max(1, bestOf);
        this.useTimeLimit = useTimeLimit;
    }

    public UUID id() { return id; }
    public UUID playerA() { return playerA; }
    public UUID playerB() { return playerB; }
    public String kitName() { return kitName; }
    public int bestOf() { return bestOf; }
    public boolean useTimeLimit() { return useTimeLimit; }
    public int roundsNeeded() { return (bestOf / 2) + 1; }

    public @Nullable Scoreboard sidebarScoreboard() { return sidebarScoreboard; }
    public void setSidebarScoreboard(@Nullable Scoreboard sb) { this.sidebarScoreboard = sb; }

    public MatchState state() { return state; }
    public void setState(MatchState state) { this.state = state; }

    public int killsA() { return killsA; }
    public int killsB() { return killsB; }
    public void incrementKillsA() { killsA++; }
    public void incrementKillsB() { killsB++; }
    public void resetKills() { killsA = 0; killsB = 0; }

    public int roundsA() { return roundsA; }
    public int roundsB() { return roundsB; }
    public void incrementRoundsA() { roundsA++; }
    public void incrementRoundsB() { roundsB++; }

    public @Nullable BukkitTask freezeTask() { return freezeTask; }
    public void setFreezeTask(@Nullable BukkitTask freezeTask) { this.freezeTask = freezeTask; }
    public @Nullable BukkitTask timerTask() { return timerTask; }
    public void setTimerTask(@Nullable BukkitTask timerTask) { this.timerTask = timerTask; }
    public @Nullable BukkitTask actionBarTask() { return actionBarTask; }
    public void setActionBarTask(@Nullable BukkitTask actionBarTask) { this.actionBarTask = actionBarTask; }

    public Map<UUID, ItemStack[]> savedInventory() { return savedInventory; }
    public Map<UUID, ItemStack[]> savedArmor() { return savedArmor; }
    public Map<UUID, ItemStack> savedOffhand() { return savedOffhand; }
    public Map<UUID, Location> savedLocation() { return savedLocation; }
    public Map<UUID, GameMode> savedGameMode() { return savedGameMode; }

    public boolean involves(UUID id) { return playerA.equals(id) || playerB.equals(id); }
    public UUID opponentOf(UUID id) {
        if (playerA.equals(id)) return playerB;
        if (playerB.equals(id)) return playerA;
        throw new IllegalArgumentException("Not in this match: " + id);
    }
}
