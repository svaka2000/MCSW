package com.samarth.duels.kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Stores named kits (the kit = a snapshot of player inventory + armor).
 * Persisted under config.yml's `kits.<name>` section using Bukkit ItemStack serialization.
 */
public final class KitRegistry {

    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        kits.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("kits");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            ConfigurationSection k = sec.getConfigurationSection(name);
            if (k == null) continue;
            Object inv = k.get("inventory");
            Object armor = k.get("armor");
            Object off = k.get("offhand");
            ItemStack[] invArr = inv instanceof List<?> l ? toArray(l) : null;
            ItemStack[] armorArr = armor instanceof List<?> l ? toArray(l) : null;
            ItemStack offhand = off instanceof ItemStack i ? i : null;
            if (invArr != null && armorArr != null) {
                kits.put(name.toLowerCase(), new Kit(name, invArr, armorArr, offhand));
            }
        }
        plugin.getLogger().info("Loaded " + kits.size() + " duel kit(s).");
    }

    /** Capture the player's current loadout into a named kit and persist. */
    public Kit saveFromPlayer(String name, Player p) {
        PlayerInventory pi = p.getInventory();
        ItemStack[] inv = pi.getContents().clone();
        ItemStack[] armor = pi.getArmorContents().clone();
        ItemStack offhand = pi.getItemInOffHand();
        Kit kit = new Kit(name, inv, armor, offhand == null || offhand.getType() == Material.AIR ? null : offhand.clone());
        kits.put(name.toLowerCase(), kit);

        String base = "kits." + name;
        // Bukkit serialization handles ItemStack[] cleanly through the ConfigurationSerializable path
        plugin.getConfig().set(base + ".inventory", listOf(inv));
        plugin.getConfig().set(base + ".armor", listOf(armor));
        plugin.getConfig().set(base + ".offhand", kit.offhand());
        plugin.saveConfig();
        return kit;
    }

    public boolean delete(String name) {
        if (kits.remove(name.toLowerCase()) == null) return false;
        plugin.getConfig().set("kits." + name, null);
        plugin.saveConfig();
        return true;
    }

    public @Nullable Kit get(String name) {
        return kits.get(name.toLowerCase());
    }

    public List<String> names() {
        return new ArrayList<>(kits.keySet());
    }

    /** Equip a kit on a player (clears their existing inventory). */
    public boolean equip(Kit kit, Player p) {
        PlayerInventory pi = p.getInventory();
        pi.clear();
        pi.setArmorContents(kit.armor());
        pi.setContents(kit.inventory());
        if (kit.offhand() != null) pi.setItemInOffHand(kit.offhand());
        return true;
    }

    private static List<ItemStack> listOf(ItemStack[] arr) {
        List<ItemStack> out = new ArrayList<>(arr.length);
        Collections.addAll(out, arr);
        return out;
    }

    private static ItemStack[] toArray(List<?> l) {
        ItemStack[] arr = new ItemStack[l.size()];
        for (int i = 0; i < l.size(); i++) {
            Object o = l.get(i);
            arr[i] = o instanceof ItemStack is ? is : null;
        }
        return arr;
    }
}
