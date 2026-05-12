package com.samarth.duels.config;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class DuelsConfig {
    private final JavaPlugin plugin;
    @Nullable private Location lobby;
    @Nullable private Location arenaA;
    @Nullable private Location arenaB;

    public DuelsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        lobby = c.get("locations.lobby") instanceof Location l ? l : null;
        arenaA = c.get("locations.arena.a") instanceof Location l ? l : null;
        arenaB = c.get("locations.arena.b") instanceof Location l ? l : null;
    }

    public int killsPerRound() { return plugin.getConfig().getInt("duels.kills-per-round", 1); }
    public int defaultBestOf() { return plugin.getConfig().getInt("duels.default-best-of", 1); }
    public int freezeSeconds() { return plugin.getConfig().getInt("duels.freeze-seconds", 3); }
    public int matchTimeCapSeconds() { return plugin.getConfig().getInt("duels.match-time-cap-seconds", 180); }
    public int challengeTimeoutSeconds() { return plugin.getConfig().getInt("duels.challenge-timeout-seconds", 30); }
    public int challengeCooldownSeconds() { return plugin.getConfig().getInt("duels.challenge-cooldown-seconds", 5); }
    public String defaultKit() { return plugin.getConfig().getString("duels.default-kit", ""); }
    public String serverIp() { return plugin.getConfig().getString("server.ip", "pvptl.com"); }

    public @Nullable Location lobby() { return lobby == null ? null : lobby.clone(); }
    public @Nullable Location arenaA() { return arenaA == null ? null : arenaA.clone(); }
    public @Nullable Location arenaB() { return arenaB == null ? null : arenaB.clone(); }

    public boolean isArenaReady() { return arenaA != null && arenaB != null; }

    public void setLobby(Location loc) {
        plugin.getConfig().set("locations.lobby", loc);
        plugin.saveConfig();
        reload();
    }

    public void setArena(char side, Location loc) {
        plugin.getConfig().set("locations.arena." + side, loc);
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
