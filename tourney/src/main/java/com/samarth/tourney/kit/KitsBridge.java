package com.samarth.tourney.kit;

import com.samarth.kits.KitService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Looks up the PvPTLKits {@link KitService} via Bukkit's ServicesManager.
 * Returns null when PvPTLKits isn't installed — callers must handle that case.
 *
 * Also provides a static {@link #clearKit(Player)} helper used by post-match
 * cleanup. Since clearing a player's inventory doesn't actually need the
 * KitService, that's intentionally a no-dep helper.
 */
public final class KitsBridge {
    private KitsBridge() {}

    public static @Nullable KitService tryGet() {
        if (Bukkit.getPluginManager().getPlugin("PvPTLKits") == null) return null;
        RegisteredServiceProvider<KitService> rsp =
            Bukkit.getServicesManager().getRegistration(KitService.class);
        return rsp == null ? null : rsp.getProvider();
    }

    /** Wipe a player's inventory + armor + offhand (post-match cleanup). */
    public static void clearKit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        inv.setItemInOffHand(new ItemStack(Material.AIR));
    }
}
