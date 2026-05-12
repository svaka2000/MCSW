package com.samarth.tourney.tournament;

import java.util.UUID;
import org.bukkit.Location;

public final class Arena {
    private final String name;
    private final Location spawnA;
    private final Location spawnB;

    public Arena(String name, Location spawnA, Location spawnB) {
        this.name = name;
        this.spawnA = spawnA.clone();
        this.spawnB = spawnB.clone();
    }

    public String name() { return name; }
    public Location spawnA() { return spawnA.clone(); }
    public Location spawnB() { return spawnB.clone(); }

    public Location spawnFor(UUID playerId, UUID slotA) {
        return playerId.equals(slotA) ? spawnA() : spawnB();
    }
}
