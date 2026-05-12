package com.samarth.tourney.kit;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and applies the fixed tournament kit:
 *   - Diamond armor full set, Protection III, Unbreakable
 *   - Diamond sword, no enchants, Unbreakable
 */
public final class KitService {

    public void apply(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(armor(Material.DIAMOND_HELMET));
        inv.setChestplate(armor(Material.DIAMOND_CHESTPLATE));
        inv.setLeggings(armor(Material.DIAMOND_LEGGINGS));
        inv.setBoots(armor(Material.DIAMOND_BOOTS));
        inv.setItem(0, sword());
    }

    public void clear(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
    }

    private ItemStack armor(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.PROTECTION, 3, true);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack sword() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }
}
