package com.samarth.duels.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class DuelsConfig {
    public static final String DEFAULT_ARENA_ID = "arena_0";

    private final JavaPlugin plugin;
    @Nullable private Location lobby;
    /** Insertion-ordered so listing is deterministic. */
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public DuelsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        lobby = c.get("locations.lobby") instanceof Location l ? l : null;

        arenas.clear();
        // New multi-arena schema: locations.arenas.<id>.spawns_a / spawns_b
        ConfigurationSection arenasSec = c.getConfigurationSection("locations.arenas");
        if (arenasSec != null) {
            for (String id : arenasSec.getKeys(false)) {
                List<Location> a = readLocList(c, "locations.arenas." + id + ".spawns_a");
                List<Location> b = readLocList(c, "locations.arenas." + id + ".spawns_b");
                arenas.put(id, new Arena(id, a, b));
            }
        }
        // Backwards compat: migrate legacy single-arena schema.
        if (arenas.isEmpty()) {
            List<Location> legacyA = readLocList(c, "locations.arena.spawns_a");
            List<Location> legacyB = readLocList(c, "locations.arena.spawns_b");
            if (c.get("locations.arena.a") instanceof Location la) legacyA.add(la);
            if (c.get("locations.arena.b") instanceof Location lb) legacyB.add(lb);
            if (!legacyA.isEmpty() || !legacyB.isEmpty()) {
                arenas.put(DEFAULT_ARENA_ID, new Arena(DEFAULT_ARENA_ID, legacyA, legacyB));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Location> readLocList(FileConfiguration c, String path) {
        Object raw = c.get(path);
        if (!(raw instanceof List<?> list)) return new ArrayList<>();
        List<Location> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Location l) out.add(l);
        }
        return out;
    }

    public int killsPerRound() { return plugin.getConfig().getInt("duels.kills-per-round", 1); }
    public int defaultFirstTo() {
        return plugin.getConfig().getInt("duels.default-first-to",
            plugin.getConfig().getInt("duels.default-best-of", 1));
    }
    public int rankedFirstTo() { return plugin.getConfig().getInt("duels.ranked-first-to", 3); }
    public int eloStarting() { return plugin.getConfig().getInt("duels.elo-starting", 1000); }
    public int eloKFactor() { return plugin.getConfig().getInt("duels.elo-k-factor", 32); }
    public int freezeSeconds() { return plugin.getConfig().getInt("duels.freeze-seconds", 3); }
    public int matchTimeCapSeconds() { return plugin.getConfig().getInt("duels.match-time-cap-seconds", 180); }
    public int challengeTimeoutSeconds() { return plugin.getConfig().getInt("duels.challenge-timeout-seconds", 30); }
    public int challengeCooldownSeconds() { return plugin.getConfig().getInt("duels.challenge-cooldown-seconds", 5); }
    public String defaultKit() { return plugin.getConfig().getString("duels.default-kit", ""); }
    public String serverIp() { return plugin.getConfig().getString("server.ip", "pvptl.com"); }
    public boolean lobbyItemsEnabled() { return plugin.getConfig().getBoolean("duels.lobby-items", true); }

    public @Nullable Location lobby() { return lobby == null ? null : lobby.clone(); }

    // ----- arena queries -----

    /** All arenas in insertion order. Never null; may be empty. */
    public List<Arena> arenas() { return new ArrayList<>(arenas.values()); }

    public @Nullable Arena arena(String id) { return arenas.get(id); }

    public int totalArenas() { return arenas.size(); }

    /** Max team size across all arenas (used to gate party-duel size checks). */
    public int maxTeamSize() {
        int best = 0;
        for (Arena a : arenas.values()) best = Math.max(best, a.maxTeamSize());
        return best;
    }

    public boolean isArenaReady() {
        for (Arena a : arenas.values()) if (a.isReady()) return true;
        return false;
    }

    // ----- legacy 1-arena accessors (back-compat for callers that haven't been refactored) -----

    public List<Location> spawnsA() {
        return arenas.isEmpty() ? List.of() : arenas.values().iterator().next().spawnsA();
    }

    public List<Location> spawnsB() {
        return arenas.isEmpty() ? List.of() : arenas.values().iterator().next().spawnsB();
    }

    public @Nullable Location arenaA() {
        List<Location> a = spawnsA();
        return a.isEmpty() ? null : a.get(0);
    }

    public @Nullable Location arenaB() {
        List<Location> b = spawnsB();
        return b.isEmpty() ? null : b.get(0);
    }

    // ----- mutators -----

    public void setLobby(Location loc) {
        plugin.getConfig().set("locations.lobby", loc);
        plugin.saveConfig();
        reload();
    }

    /**
     * Write a spawn into a specific arena+side+slot.
     * Pass {@code slot < 0} to append. Creates the arena if it doesn't exist.
     * Returns the slot index actually written.
     */
    public int setSpawn(String arenaId, char side, int slot, Location loc) {
        Arena existing = arenas.get(arenaId);
        List<Location> a = existing == null ? new ArrayList<>() : new ArrayList<>(existing.spawnsA());
        List<Location> b = existing == null ? new ArrayList<>() : new ArrayList<>(existing.spawnsB());
        boolean sideA = side == 'a' || side == 'A';
        List<Location> target = sideA ? a : b;
        int idx;
        if (slot < 0 || slot >= target.size()) {
            target.add(loc);
            idx = target.size() - 1;
        } else {
            target.set(slot, loc);
            idx = slot;
        }
        plugin.getConfig().set("locations.arenas." + arenaId + ".spawns_a", a);
        plugin.getConfig().set("locations.arenas." + arenaId + ".spawns_b", b);
        // Clear legacy single-spawn keys if they were the source of the original data.
        if (DEFAULT_ARENA_ID.equals(arenaId)) {
            plugin.getConfig().set("locations.arena", null);
        }
        plugin.saveConfig();
        reload();
        return idx;
    }

    /** Remove all spawns on a side of one arena. Pass 'x' to wipe both sides. */
    public void clearSpawns(String arenaId, char side) {
        Arena existing = arenas.get(arenaId);
        if (existing == null) return;
        boolean sideA = side == 'a' || side == 'A';
        boolean sideB = side == 'b' || side == 'B';
        boolean both = !sideA && !sideB;
        List<Location> a = both || sideA ? Collections.emptyList() : existing.spawnsA();
        List<Location> b = both || sideB ? Collections.emptyList() : existing.spawnsB();
        plugin.getConfig().set("locations.arenas." + arenaId + ".spawns_a", a);
        plugin.getConfig().set("locations.arenas." + arenaId + ".spawns_b", b);
        plugin.saveConfig();
        reload();
    }

    /** Wipe an arena entry entirely. */
    public void deleteArena(String arenaId) {
        plugin.getConfig().set("locations.arenas." + arenaId, null);
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
