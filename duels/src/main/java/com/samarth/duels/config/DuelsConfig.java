package com.samarth.duels.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class DuelsConfig {
    private final JavaPlugin plugin;
    @Nullable private Location lobby;
    private final List<Location> spawnsA = new ArrayList<>();
    private final List<Location> spawnsB = new ArrayList<>();

    public DuelsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        lobby = c.get("locations.lobby") instanceof Location l ? l : null;

        spawnsA.clear();
        spawnsB.clear();
        // New multi-spawn format: locations.arena.spawns_a / spawns_b as lists.
        addLocations(c, "locations.arena.spawns_a", spawnsA);
        addLocations(c, "locations.arena.spawns_b", spawnsB);
        // Backwards compat: read old single-location keys if the new lists are empty.
        if (spawnsA.isEmpty() && c.get("locations.arena.a") instanceof Location la) spawnsA.add(la);
        if (spawnsB.isEmpty() && c.get("locations.arena.b") instanceof Location lb) spawnsB.add(lb);
    }

    @SuppressWarnings("unchecked")
    private static void addLocations(FileConfiguration c, String path, List<Location> into) {
        Object raw = c.get(path);
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Location l) into.add(l);
            }
        }
    }

    public int killsPerRound() { return plugin.getConfig().getInt("duels.kills-per-round", 1); }
    public int defaultFirstTo() {
        return plugin.getConfig().getInt("duels.default-first-to",
            plugin.getConfig().getInt("duels.default-best-of", 1));
    }
    public int freezeSeconds() { return plugin.getConfig().getInt("duels.freeze-seconds", 3); }
    public int matchTimeCapSeconds() { return plugin.getConfig().getInt("duels.match-time-cap-seconds", 180); }
    public int challengeTimeoutSeconds() { return plugin.getConfig().getInt("duels.challenge-timeout-seconds", 30); }
    public int challengeCooldownSeconds() { return plugin.getConfig().getInt("duels.challenge-cooldown-seconds", 5); }
    public String defaultKit() { return plugin.getConfig().getString("duels.default-kit", ""); }
    public String serverIp() { return plugin.getConfig().getString("server.ip", "pvptl.com"); }

    public @Nullable Location lobby() { return lobby == null ? null : lobby.clone(); }

    /** All spawn points for side A (in setup order). Never null; may be empty. */
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

    /** Convenience: first spawn of side A (used by legacy 1v1 paths). */
    public @Nullable Location arenaA() { return spawnsA.isEmpty() ? null : spawnsA.get(0).clone(); }
    public @Nullable Location arenaB() { return spawnsB.isEmpty() ? null : spawnsB.get(0).clone(); }

    public int maxTeamSize() { return Math.min(spawnsA.size(), spawnsB.size()); }

    public boolean isArenaReady() { return !spawnsA.isEmpty() && !spawnsB.isEmpty(); }

    public void setLobby(Location loc) {
        plugin.getConfig().set("locations.lobby", loc);
        plugin.saveConfig();
        reload();
    }

    /**
     * Set a specific spawn slot. If slot is &lt; 0 the spawn is appended.
     * Returns the slot index actually written.
     */
    public int setSpawn(char side, int slot, Location loc) {
        String path = side == 'a' || side == 'A' ? "locations.arena.spawns_a" : "locations.arena.spawns_b";
        List<Location> list = side == 'a' || side == 'A' ? spawnsA : spawnsB;
        List<Location> mutable = new ArrayList<>(list);
        int idx;
        if (slot < 0 || slot >= mutable.size()) {
            mutable.add(loc);
            idx = mutable.size() - 1;
        } else {
            mutable.set(slot, loc);
            idx = slot;
        }
        plugin.getConfig().set(path, mutable);
        // Clear legacy single-spawn keys if present (we own this side now).
        plugin.getConfig().set("locations.arena." + (side == 'a' || side == 'A' ? "a" : "b"), null);
        plugin.saveConfig();
        reload();
        return idx;
    }

    /** Remove all spawns on the given side. */
    public void clearSpawns(char side) {
        String path = side == 'a' || side == 'A' ? "locations.arena.spawns_a" : "locations.arena.spawns_b";
        plugin.getConfig().set(path, Collections.emptyList());
        plugin.getConfig().set("locations.arena." + (side == 'a' || side == 'A' ? "a" : "b"), null);
        plugin.saveConfig();
        reload();
    }

    public String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "<red>missing message: " + key + "</red>");
    }

    public String prefix() { return msg("prefix"); }

    public static String describe(@Nullable Location loc) {
        if (loc == null) return "unset";
        return String.format("%s @ %.1f, %.1f, %.1f",
            loc.getWorld() == null ? "?" : loc.getWorld().getName(),
            loc.getX(), loc.getY(), loc.getZ());
    }
}
