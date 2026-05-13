package com.samarth.duels.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

/**
 * One duel — supports N vs N teams. 1v1 is just a team of size 1 on each side.
 *
 * Round semantics:
 *   - Each player is marked "dead in round" when they die.
 *   - The round ends when one entire team is dead.
 *   - Round winner gets a round win; first to {@code firstTo} round wins takes the match.
 *   - Between rounds, every player is revived at their team spawn and re-kitted.
 *   - kills counters are reset between rounds; round counters persist for the match.
 */
public final class DuelMatch {
    private final UUID id = UUID.randomUUID();
    private final List<UUID> teamA;
    private final List<UUID> teamB;
    private final String kitName;
    private final int firstTo;
    private final boolean useTimeLimit;

    private MatchState state = MatchState.PENDING;
    private int killsA;
    private int killsB;
    private int roundsA;
    private int roundsB;
    private long startMillis;

    private final Set<UUID> deadInRound = new HashSet<>();

    @Nullable private BukkitTask freezeTask;
    @Nullable private BukkitTask timerTask;
    @Nullable private BukkitTask actionBarTask;
    @Nullable private Scoreboard sidebarScoreboard;

    private final Map<UUID, ItemStack[]> savedInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, ItemStack> savedOffhand = new HashMap<>();
    private final Map<UUID, Location> savedLocation = new HashMap<>();
    private final Map<UUID, GameMode> savedGameMode = new HashMap<>();

    public DuelMatch(List<UUID> teamA, List<UUID> teamB, String kitName, int firstTo, boolean useTimeLimit) {
        this.teamA = List.copyOf(teamA);
        this.teamB = List.copyOf(teamB);
        this.kitName = kitName;
        this.firstTo = Math.max(1, firstTo);
        this.useTimeLimit = useTimeLimit;
    }

    public UUID id() { return id; }
    public List<UUID> teamA() { return teamA; }
    public List<UUID> teamB() { return teamB; }
    public String kitName() { return kitName; }
    public int firstTo() { return firstTo; }
    public boolean useTimeLimit() { return useTimeLimit; }
    public int roundsNeeded() { return firstTo; }

    public boolean isTeamMatch() { return teamA.size() > 1 || teamB.size() > 1; }

    /** Legacy 1v1 accessors — slot 0 of each team. */
    public UUID playerA() { return teamA.get(0); }
    public UUID playerB() { return teamB.get(0); }

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

    public long startMillisOrZero() { return startMillis; }
    public void markStartedNow() { if (startMillis == 0) startMillis = System.currentTimeMillis(); }

    // ----- alive / dead in this round -----

    public boolean isDeadInRound(UUID id) { return deadInRound.contains(id); }
    public void markDeadInRound(UUID id) { deadInRound.add(id); }
    public void clearDeadInRound() { deadInRound.clear(); }
    public boolean teamAFullyDead() { return deadInRound.containsAll(teamA); }
    public boolean teamBFullyDead() { return deadInRound.containsAll(teamB); }

    // ----- team queries -----

    public boolean involves(UUID id) { return teamA.contains(id) || teamB.contains(id); }

    /** 1 = team A, 2 = team B, 0 = not in this match. */
    public int teamOf(UUID id) {
        if (teamA.contains(id)) return 1;
        if (teamB.contains(id)) return 2;
        return 0;
    }

    public List<UUID> opposingTeamOf(UUID id) {
        if (teamA.contains(id)) return teamB;
        if (teamB.contains(id)) return teamA;
        return List.of();
    }

    public List<UUID> sameTeamAs(UUID id) {
        if (teamA.contains(id)) return teamA;
        if (teamB.contains(id)) return teamB;
        return List.of();
    }

    /** Index of {@code id} in its own team list. Used to pick the matching arena spawn slot. */
    public int slotOf(UUID id) {
        int i = teamA.indexOf(id);
        if (i >= 0) return i;
        return teamB.indexOf(id);
    }

    /** Team A then Team B in insertion order. */
    public List<UUID> allPlayers() {
        List<UUID> all = new ArrayList<>(teamA.size() + teamB.size());
        all.addAll(teamA);
        all.addAll(teamB);
        return all;
    }

    // ----- tasks + scoreboard -----

    public @Nullable BukkitTask freezeTask() { return freezeTask; }
    public void setFreezeTask(@Nullable BukkitTask t) { this.freezeTask = t; }
    public @Nullable BukkitTask timerTask() { return timerTask; }
    public void setTimerTask(@Nullable BukkitTask t) { this.timerTask = t; }
    public @Nullable BukkitTask actionBarTask() { return actionBarTask; }
    public void setActionBarTask(@Nullable BukkitTask t) { this.actionBarTask = t; }
    public @Nullable Scoreboard sidebarScoreboard() { return sidebarScoreboard; }
    public void setSidebarScoreboard(@Nullable Scoreboard sb) { this.sidebarScoreboard = sb; }

    // ----- saved pre-match state -----

    public Map<UUID, ItemStack[]> savedInventory() { return savedInventory; }
    public Map<UUID, ItemStack[]> savedArmor() { return savedArmor; }
    public Map<UUID, ItemStack> savedOffhand() { return savedOffhand; }
    public Map<UUID, Location> savedLocation() { return savedLocation; }
    public Map<UUID, GameMode> savedGameMode() { return savedGameMode; }
}
