package com.samarth.duels.ui;

import com.samarth.duels.kit.Kit;
import com.samarth.duels.kit.KitRegistry;
import com.samarth.duels.queue.QueueService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class QueueGui {
    public static final String TITLE = "Duels Queue";

    public static Inventory build(KitRegistry kits, QueueService queues) {
        List<String> names = kits.names();
        int rows = Math.max(1, Math.min(6, (names.size() + 8) / 9 + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
            Component.text(TITLE, NamedTextColor.AQUA));
        for (int i = 0; i < names.size() && i < rows * 9 - 1; i++) {
            String name = names.get(i);
            inv.setItem(i, kitIcon(name, queues));
        }
        inv.setItem(rows * 9 - 1, leaveButton());
        return inv;
    }

    private static ItemStack kitIcon(String name, QueueService queues) {
        ItemStack it = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("In queue: " + queues.queueSize(name), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Component.text("Click to queue", NamedTextColor.GREEN));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private static ItemStack leaveButton() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Leave queue", NamedTextColor.RED));
            it.setItemMeta(meta);
        }
        return it;
    }

    public static void open(Player p, KitRegistry kits, QueueService queues) {
        p.openInventory(build(kits, queues));
    }
}
