package com.samarth.duels.listeners;

import com.samarth.duels.challenge.ChallengeService;
import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitsBridge;
import com.samarth.duels.queue.QueueService;
import com.samarth.duels.ui.DuelCustomizeHolder;
import com.samarth.duels.ui.DuelSetupHolder;
import com.samarth.duels.ui.QueueGui;
import com.samarth.kits.KitService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * One listener handling all duel-related entity right-clicks and inventory GUI clicks.
 *
 *  - Right-clicking an entity tagged "duels_queue_npc" opens the queue GUI.
 *  - Clicks inside the QueueGui hand off to queue service.
 *  - Clicks inside DuelSetupHolder → send challenge or open customize GUI.
 *  - Clicks inside DuelCustomizeHolder → adjust settings or send/cancel.
 */
public final class NpcInteractionListener implements Listener {
    public static final String QUEUE_NPC_TAG = "duels_queue_npc";

    private final QueueService queues;
    private final ChallengeService challenges;
    private final DuelsConfig config;

    public NpcInteractionListener(QueueService queues,
                                  ChallengeService challenges, DuelsConfig config) {
        this.queues = queues;
        this.challenges = challenges;
        this.config = config;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        if (!target.getScoreboardTags().contains(QUEUE_NPC_TAG)) return;
        KitService kits = KitsBridge.tryGet();
        if (kits == null) {
            e.getPlayer().sendMessage("§cPvPTLKits not loaded — duels disabled.");
            e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        QueueGui.open(e.getPlayer(), kits, queues);
    }

    /**
     * Right-clicking the tagged leave-queue barrier (anywhere in the player's inventory)
     * dequeues the player. The barrier is given to queued players in their hotbar.
     */
    @EventHandler
    public void onInteractItem(PlayerInteractEvent e) {
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.BARRIER) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(queues.leaveQueueKey(), PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        queues.dequeue(e.getPlayer(), true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        InventoryHolder holder = inv.getHolder();
        if (holder instanceof DuelSetupHolder setup) {
            e.setCancelled(true);
            handleSetupClick(e, setup);
            return;
        }
        if (holder instanceof DuelCustomizeHolder cust) {
            e.setCancelled(true);
            handleCustomizeClick(e, cust);
            return;
        }
        // QueueGui uses title-based detection (no holder)
        String title = PlainTextComponentSerializer.plainText().serialize(e.getView().title());
        if (QueueGui.TITLE.equals(title)) {
            e.setCancelled(true);
            handleQueueGuiClick(e);
        }
    }

    private void handleSetupClick(InventoryClickEvent e, DuelSetupHolder setup) {
        if (!(e.getWhoClicked() instanceof Player viewer)) return;
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() != Material.DIAMOND_SWORD) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        Component nameComp = meta.displayName();
        if (nameComp == null) return;
        String kitName = PlainTextComponentSerializer.plainText().serialize(nameComp);
        KitService kits = KitsBridge.tryGet();
        if (kits == null || kits.get(kitName) == null) return;

        Player target = Bukkit.getPlayer(setup.target());
        if (target == null) {
            viewer.sendMessage("§cTarget went offline.");
            viewer.closeInventory();
            return;
        }

        if (e.getClick().isRightClick()) {
            viewer.closeInventory();
            DuelCustomizeHolder cust = new DuelCustomizeHolder(
                target.getUniqueId(), target.getName(), kitName,
                config.defaultFirstTo(), false);
            viewer.openInventory(cust.build());
        } else {
            // Left-click — send with defaults (no time limit)
            viewer.closeInventory();
            challenges.challenge(viewer, target, kitName, config.defaultFirstTo(), false);
        }
    }

    private void handleCustomizeClick(InventoryClickEvent e, DuelCustomizeHolder cust) {
        if (!(e.getWhoClicked() instanceof Player viewer)) return;
        int slot = e.getRawSlot();
        boolean refresh = true;
        switch (slot) {
            case DuelCustomizeHolder.SLOT_ROUNDS_1 -> cust.setRounds(1);
            case DuelCustomizeHolder.SLOT_ROUNDS_3 -> cust.setRounds(3);
            case DuelCustomizeHolder.SLOT_ROUNDS_5 -> cust.setRounds(5);
            case DuelCustomizeHolder.SLOT_ROUNDS_7 -> cust.setRounds(7);
            case DuelCustomizeHolder.SLOT_TIME_LIMIT -> cust.setUseTimeLimit(!cust.useTimeLimit());
            case DuelCustomizeHolder.SLOT_CANCEL -> {
                viewer.closeInventory();
                return;
            }
            case DuelCustomizeHolder.SLOT_SEND -> {
                Player target = Bukkit.getPlayer(cust.target());
                viewer.closeInventory();
                if (target == null) {
                    viewer.sendMessage("§cTarget went offline.");
                    return;
                }
                challenges.challenge(viewer, target, cust.kitName(), cust.rounds(), cust.useTimeLimit());
                return;
            }
            default -> refresh = false;
        }
        if (refresh) cust.populate();
    }

    private void handleQueueGuiClick(InventoryClickEvent e) {
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
        KitService kits = KitsBridge.tryGet();
        if (kits == null || kits.get(name) == null) return;
        viewer.closeInventory();
        queues.enqueue(viewer, name);
    }
}
