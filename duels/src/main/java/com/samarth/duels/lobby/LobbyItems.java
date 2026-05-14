package com.samarth.duels.lobby;

import java.util.Collections;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Two-item lobby hotbar:
 *   slot 0: diamond sword → opens the RANKED queue GUI
 *   slot 1: iron sword    → opens the UNRANKED queue GUI
 *
 * Items are tagged in their PDC so the click listener can route right-clicks
 * without relying on display-name parsing.
 */
public final class LobbyItems {
    public static final int SLOT_RANKED = 0;
    public static final int SLOT_UNRANKED = 1;

    private final NamespacedKey rankedKey;
    private final NamespacedKey unrankedKey;

    public LobbyItems(JavaPlugin plugin) {
        this.rankedKey = new NamespacedKey(plugin, "lobby_ranked_picker");
        this.unrankedKey = new NamespacedKey(plugin, "lobby_unranked_picker");
    }

    public NamespacedKey rankedKey() { return rankedKey; }
    public NamespacedKey unrankedKey() { return unrankedKey; }

    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.setItem(SLOT_RANKED, buildRankedSword());
        inv.setItem(SLOT_UNRANKED, buildUnrankedSword());
    }

    public void remove(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            var pdc = meta.getPersistentDataContainer();
            if (pdc.has(rankedKey, PersistentDataType.BYTE)
                || pdc.has(unrankedKey, PersistentDataType.BYTE)) {
                inv.setItem(i, null);
            }
        }
    }

    public boolean isRankedSword(ItemStack it) {
        if (it == null) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().has(rankedKey, PersistentDataType.BYTE);
    }

    public boolean isUnrankedSword(ItemStack it) {
        if (it == null) return false;
        ItemMeta meta = it.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().has(unrankedKey, PersistentDataType.BYTE);
    }

    private ItemStack buildRankedSword() {
        ItemStack it = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Ranked Queue", NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(Collections.singletonList(
                Component.text("Right-click to pick a kit and queue ranked", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(rankedKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack buildUnrankedSword() {
        ItemStack it = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Unranked Queue", NamedTextColor.GRAY, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(Collections.singletonList(
                Component.text("Right-click to pick a kit and queue casual", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(unrankedKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }
}
