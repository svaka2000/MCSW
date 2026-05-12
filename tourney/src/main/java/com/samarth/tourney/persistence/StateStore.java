package com.samarth.tourney.persistence;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/** Saves player inventories + locations to disk so they survive crashes/reloads. */
public final class StateStore {
    private final JavaPlugin plugin;
    private final File dir;

    public StateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "saved");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create state directory: " + dir);
        }
    }

    public void save(Player p) {
        File f = file(p.getUniqueId());
        YamlConfiguration y = new YamlConfiguration();
        y.set("inv", p.getInventory().getContents());
        y.set("armor", p.getInventory().getArmorContents());
        y.set("location", p.getLocation());
        y.set("gamemode", p.getGameMode().name());
        y.set("health", p.getHealth());
        y.set("food", p.getFoodLevel());
        try {
            y.save(f);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save state for " + p.getName() + ": " + e.getMessage());
        }
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    public @Nullable Saved load(UUID id) {
        File f = file(id);
        if (!f.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        Object invObj = y.get("inv");
        Object armorObj = y.get("armor");
        Object locObj = y.get("location");
        String modeStr = y.getString("gamemode");
        double health = y.getDouble("health", 20.0);
        int food = y.getInt("food", 20);
        ItemStack[] inv = invObj instanceof ItemStack[] arr ? arr : null;
        ItemStack[] armor = armorObj instanceof ItemStack[] arr ? arr : null;
        Location loc = locObj instanceof Location l ? l : null;
        GameMode mode = modeStr == null ? GameMode.SURVIVAL : safeMode(modeStr);
        return new Saved(inv, armor, loc, mode, health, food);
    }

    public void clear(UUID id) {
        File f = file(id);
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Failed to delete saved state file: " + f);
        }
    }

    public void apply(Player p, Saved s) {
        if (s.inv != null) p.getInventory().setContents(s.inv);
        if (s.armor != null) p.getInventory().setArmorContents(s.armor);
        if (s.location != null) p.teleport(s.location);
        if (s.gameMode != null) p.setGameMode(s.gameMode);
        p.setHealth(Math.max(1.0, Math.min(20.0, s.health)));
        p.setFoodLevel(s.food);
    }

    private GameMode safeMode(String name) {
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return GameMode.SURVIVAL;
        }
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    /** Restore any leftover state for a player who joined after a crash. */
    public boolean restoreIfPresent(Player p) {
        Saved s = load(p.getUniqueId());
        if (s == null) return false;
        apply(p, s);
        clear(p.getUniqueId());
        Bukkit.getLogger().info("[Tourney] Restored saved state for " + p.getName());
        return true;
    }

    public static final class Saved {
        public final @Nullable ItemStack[] inv;
        public final @Nullable ItemStack[] armor;
        public final @Nullable Location location;
        public final GameMode gameMode;
        public final double health;
        public final int food;

        public Saved(@Nullable ItemStack[] inv, @Nullable ItemStack[] armor, @Nullable Location location,
                     GameMode gameMode, double health, int food) {
            this.inv = inv;
            this.armor = armor;
            this.location = location;
            this.gameMode = gameMode;
            this.health = health;
            this.food = food;
        }
    }
}
