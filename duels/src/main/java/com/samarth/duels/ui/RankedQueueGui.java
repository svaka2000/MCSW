package com.samarth.duels.ui;

import com.samarth.duels.queue.QueueService;
import com.samarth.kits.KitService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Ranked queue picker. One row of kit buttons; click a kit to enqueue ranked.
 * Title-based detection (mirrors {@link QueueGui}); no holder, so opening it
 * doesn't require a stateful object on the player side.
 */
public final class RankedQueueGui {
    public static final String TITLE = "Ranked Queue";

    private RankedQueueGui() {}

    public static void open(Player p, KitService kits, QueueService queues) {
        List<String> names = kits.names();
        int rows = Math.max(1, Math.min(6, (names.size() + 8) / 9));
        Inventory inv = Bukkit.createInventory(p, rows * 9,
            Component.text(TITLE, NamedTextColor.AQUA));
        for (int i = 0; i < names.size() && i < rows * 9; i++) {
            inv.setItem(i, kitIcon(names.get(i)));
        }
        p.openInventory(inv);
    }

    private static ItemStack kitIcon(String name) {
        ItemStack it = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to join the ranked queue for this kit",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("First to 3 rounds. Elo-tracked.",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    /** Returns true if the click matched a kit icon (and we enqueued). */
    public static boolean handleClick(InventoryClickEvent e, Player viewer,
                                      KitService kits, QueueService queues) {
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        Component nameComp = meta.displayName();
        if (nameComp == null) return false;
        String name = PlainTextComponentSerializer.plainText().serialize(nameComp);
        if (kits.get(name) == null) return false;
        viewer.closeInventory();
        queues.enqueue(viewer, name, true);
        return true;
    }
}
