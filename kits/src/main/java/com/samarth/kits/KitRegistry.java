package com.samarth.kits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Default {@link KitService} implementation backed by Bukkit's YAML config
 * serialization. Kits are stored under config.yml's {@code kits.<name>} section.
 */
public final class KitRegistry implements KitService {

    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void reload() {
        plugin.reloadConfig();
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
        plugin.getLogger().info("Loaded " + kits.size() + " kit(s).");
    }

    @Override
    public Kit saveFromPlayer(String name, Player p) {
        PlayerInventory pi = p.getInventory();
        ItemStack[] inv = pi.getContents().clone();
        ItemStack[] armor = pi.getArmorContents().clone();
        ItemStack offhand = pi.getItemInOffHand();
        Kit kit = new Kit(name, inv, armor,
            offhand == null || offhand.getType() == Material.AIR ? null : offhand.clone());
        kits.put(name.toLowerCase(), kit);

        String base = "kits." + name;
        plugin.getConfig().set(base + ".inventory", listOf(inv));
        plugin.getConfig().set(base + ".armor", listOf(armor));
        plugin.getConfig().set(base + ".offhand", kit.offhand());
        plugin.saveConfig();
        return kit;
    }

    @Override
    public boolean delete(String name) {
        if (kits.remove(name.toLowerCase()) == null) return false;
        plugin.getConfig().set("kits." + name, null);
        plugin.saveConfig();
        return true;
    }

    @Override
    public @Nullable Kit get(String name) {
        return kits.get(name.toLowerCase());
    }

    @Override
    public List<String> names() {
        List<String> out = new ArrayList<>(kits.keySet());
        Collections.sort(out);
        return out;
    }

    @Override
    public boolean equip(String name, Player p) {
        Kit kit = get(name);
        if (kit == null) return false;
        PlayerInventory pi = p.getInventory();
        pi.clear();
        pi.setArmorContents(kit.armor());
        pi.setContents(kit.inventory());
        if (kit.offhand() != null) pi.setItemInOffHand(kit.offhand());
        // Refresh own HUD (otherwise the wearer sometimes sees ghost-armor slots).
        p.updateInventory();
        // Broadcast equipment to other clients. Bukkit's setArmorContents() doesn't
        // always send the equipment-change packet to nearby viewers (especially when
        // the wearer was just teleported / respawned), which is what made the kit
        // look like "no armor" from the opponent's POV. Doing it explicitly here
        // forces a fresh equipment packet to land after the teleport.
        broadcastEquipment(p);
        return true;
    }

    private static void broadcastEquipment(Player p) {
        PlayerInventory pi = p.getInventory();
        EnumMap<EquipmentSlot, ItemStack> slots = new EnumMap<>(EquipmentSlot.class);
        slots.put(EquipmentSlot.HEAD, nullToAir(pi.getHelmet()));
        slots.put(EquipmentSlot.CHEST, nullToAir(pi.getChestplate()));
        slots.put(EquipmentSlot.LEGS, nullToAir(pi.getLeggings()));
        slots.put(EquipmentSlot.FEET, nullToAir(pi.getBoots()));
        slots.put(EquipmentSlot.HAND, nullToAir(pi.getItemInMainHand()));
        slots.put(EquipmentSlot.OFF_HAND, nullToAir(pi.getItemInOffHand()));
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(p)) continue;
            if (!viewer.getWorld().equals(p.getWorld())) continue;
            try {
                viewer.sendEquipmentChange(p, slots);
            } catch (Throwable ignored) {
                // Older Paper API or unrelated entity — silently skip; the next
                // hide/show cycle from the match runner will refresh anyway.
            }
        }
    }

    private static ItemStack nullToAir(@Nullable ItemStack it) {
        return it == null ? new ItemStack(Material.AIR) : it;
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
