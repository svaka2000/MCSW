package com.samarth.duels.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

/**
 * One physical arena instance — a set of spawn slots for side A and side B.
 * Multiple arenas can be configured so matches run in parallel; each match
 * occupies one arena exclusively while it's live.
 */
public final class Arena {
    private final String id;
    private final List<Location> spawnsA;
    private final List<Location> spawnsB;

    public Arena(String id, List<Location> spawnsA, List<Location> spawnsB) {
        this.id = id;
        // Defensive copies so callers can't mutate our state.
        this.spawnsA = List.copyOf(spawnsA);
        this.spawnsB = List.copyOf(spawnsB);
    }

    public String id() { return id; }

    public List<Location> spawnsA() {
        List<Location> out = new ArrayList<>(spawnsA.size());
        for (Location l : spawnsA) out.add(l.clone());
        return out;
    }

    public List<Location> spawnsB() {
        List<Location> out = new ArrayList<>(spawnsB.size());
        for (Location l : spawnsB) out.add(l.clone());
        return out;
    }

    /** Largest balanced team size this arena supports (min of side A / side B counts). */
    public int maxTeamSize() { return Math.min(spawnsA.size(), spawnsB.size()); }

    public boolean isReady() { return !spawnsA.isEmpty() && !spawnsB.isEmpty(); }

    public boolean canFit(int teamASize, int teamBSize) {
        return spawnsA.size() >= teamASize && spawnsB.size() >= teamBSize;
    }
}
