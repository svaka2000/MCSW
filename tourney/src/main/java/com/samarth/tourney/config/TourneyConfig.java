package com.samarth.tourney.config;

import com.samarth.tourney.tournament.Arena;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class TourneyConfig {
    private final JavaPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    @Nullable private Location lobby;

    public TourneyConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();
        arenas.clear();
        lobby = null;

        Object lobbyObj = cfg.get("locations.lobby");
        if (lobbyObj instanceof Location l) {
            lobby = l;
        }

        ConfigurationSection arenaSec = cfg.getConfigurationSection("locations.arenas");
        if (arenaSec != null) {
            for (String name : arenaSec.getKeys(false)) {
                Object aObj = arenaSec.get(name + ".a");
                Object bObj = arenaSec.get(name + ".b");
                if (aObj instanceof Location la && bObj instanceof Location lb) {
                    arenas.put(name, new Arena(name, la, lb));
                } else {
                    plugin.getLogger().warning("Arena '" + name + "' has invalid spawn(s); skipping.");
                }
            }
        }
    }

    public void save() {
        plugin.saveConfig();
    }

    public int joinWindowSeconds() { return plugin.getConfig().getInt("tournament.join-window-seconds", 300); }
    public int minPlayers() { return plugin.getConfig().getInt("tournament.min-players", 2); }
    public int maxPlayers() { return plugin.getConfig().getInt("tournament.max-players", 64); }
    public int matchTimeCapSeconds() { return plugin.getConfig().getInt("tournament.match-time-cap-seconds", 300); }
    public int killsToWin() { return plugin.getConfig().getInt("tournament.kills-to-win", 5); }
    public int freezeSeconds() { return plugin.getConfig().getInt("tournament.freeze-seconds", 5); }
    public int disconnectGraceSeconds() { return plugin.getConfig().getInt("tournament.disconnect-grace-seconds", 60); }

    public @Nullable Location lobby() { return lobby == null ? null : lobby.clone(); }
    public Map<String, Arena> arenas() { return arenas; }

    public String msg(String key) {
        return plugin.getConfig().getString("messages." + key, "<red>missing message: " + key + "</red>");
    }

    public String prefix() { return msg("prefix"); }

    /** Save a lobby location to config (and disk) under locations.lobby. */
    public void setLobby(Location loc) {
        plugin.getConfig().set("locations.lobby", loc);
        save();
        reload();
    }

    /** Save (or update) a single side of an arena. */
    public void setArenaSpawn(String name, char slot, Location loc) {
        String path = "locations.arenas." + name + "." + slot;
        plugin.getConfig().set(path, loc);
        save();
        reload();
    }

    public void removeArena(String name) {
        plugin.getConfig().set("locations.arenas." + name, null);
        save();
        reload();
    }

    public Map<String, Map<String, Boolean>> arenaCompletionStatus() {
        Map<String, Map<String, Boolean>> out = new HashMap<>();
        ConfigurationSection arenaSec = plugin.getConfig().getConfigurationSection("locations.arenas");
        if (arenaSec == null) return out;
        for (String name : arenaSec.getKeys(false)) {
            Map<String, Boolean> sides = new HashMap<>();
            sides.put("a", arenaSec.get(name + ".a") instanceof Location);
            sides.put("b", arenaSec.get(name + ".b") instanceof Location);
            out.put(name, sides);
        }
        return out;
    }

    /** Used to detect bad/no setup before allowing a tournament. */
    public boolean isReady() {
        if (lobby == null) return false;
        if (arenas.isEmpty()) return false;
        return true;
    }

    public static String describeLocation(@Nullable Location loc) {
        if (loc == null) return "unset";
        return String.format("%s @ %.1f, %.1f, %.1f",
            loc.getWorld() == null ? "?" : loc.getWorld().getName(),
            loc.getX(), loc.getY(), loc.getZ());
    }

    @SuppressWarnings("unused")
    private static String resolveWorldName(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return Bukkit.getWorlds().get(0).getName();
        }
        return loc.getWorld().getName();
    }
}
