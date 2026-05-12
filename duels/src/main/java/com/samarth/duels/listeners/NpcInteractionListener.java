package com.samarth.duels.listeners;

import com.samarth.duels.kit.KitRegistry;
import com.samarth.duels.queue.QueueService;
import com.samarth.duels.ui.QueueGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Right-clicking any entity with the "duels_queue_npc" scoreboard tag opens the queue GUI.
 * Operators set the tag with /duels tagentity (while looking at the entity).
 * Also handles clicks inside the QueueGui itself.
 */
public final class NpcInteractionListener implements Listener {
    public static final String QUEUE_NPC_TAG = "duels_queue_npc";

    private final KitRegistry kits;
    private final QueueService queues;

    public NpcInteractionListener(KitRegistry kits, QueueService queues) {
        this.kits = kits;
        this.queues = queues;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        if (!target.getScoreboardTags().contains(QUEUE_NPC_TAG)) return;
        e.setCancelled(true);
        QueueGui.open(e.getPlayer(), kits, queues);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (!QueueGui.TITLE.equals(title)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player viewer)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Component nameComp = meta.displayName();
        if (nameComp == null) return;
        String name = PlainTextComponentSerializer.plainText().serialize(nameComp);

        if ("Leave queue".equals(name)) {
            queues.dequeue(viewer, true);
            viewer.closeInventory();
            return;
        }

        // Otherwise interpret as a kit name (it is the kit's display name)
        if (kits.get(name) == null) return;
        viewer.closeInventory();
        queues.enqueue(viewer, name);
    }
}
