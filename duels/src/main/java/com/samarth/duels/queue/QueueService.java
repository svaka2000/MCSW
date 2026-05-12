package com.samarth.duels.queue;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitRegistry;
import com.samarth.duels.match.MatchRunner;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class QueueService {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Hotbar slot the leave-queue barrier is placed in (slot 8 = far right). */
    private static final int LEAVE_BARRIER_SLOT = 8;

    private final JavaPlugin plugin;
    private final DuelsConfig config;
    private final KitRegistry kits;
    private final MatchRunner runner;

    private final Map<String, Deque<UUID>> queuesByKit = new HashMap<>();
    private final Map<UUID, String> kitByPlayer = new HashMap<>();
    /** Original slot-8 contents we displaced when giving the leave barrier. */
    private final Map<UUID, ItemStack> savedHotbarSlot = new HashMap<>();
    private final NamespacedKey leaveQueueKey;

    public QueueService(JavaPlugin plugin, DuelsConfig config, KitRegistry kits, MatchRunner runner) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.runner = runner;
        this.leaveQueueKey = new NamespacedKey(plugin, "leave_queue_item");
    }

    public NamespacedKey leaveQueueKey() { return leaveQueueKey; }

    public void enqueue(Player p, String kitName) {
        if (kitName == null || kitName.isBlank()) {
            send(p, "<red>You must specify a kit. Try /duels gui or /duels queue <kit>.</red>");
            return;
        }
        if (kits.get(kitName) == null) {
            send(p, "<red>Kit '" + kitName + "' doesn't exist. Use /duelkit list to see available kits.</red>");
            return;
        }
        if (runner.isInMatch(p.getUniqueId())) {
            send(p, "<red>You're already in a duel.</red>");
            return;
        }
        // Already queued? Leave the old queue first.
        if (kitByPlayer.containsKey(p.getUniqueId())) {
            dequeue(p, false);
        }
        Deque<UUID> q = queuesByKit.computeIfAbsent(kitName.toLowerCase(), k -> new ArrayDeque<>());
        q.addLast(p.getUniqueId());
        kitByPlayer.put(p.getUniqueId(), kitName.toLowerCase());

        giveLeaveBarrier(p);

        send(p, config.msg("queued")
            .replace("<kit>", kitName)
            .replace("<count>", String.valueOf(q.size())));
        tryMatch(kitName.toLowerCase());
    }

    public void dequeue(Player p, boolean notify) {
        String kit = kitByPlayer.remove(p.getUniqueId());
        removeLeaveBarrier(p);
        if (kit == null) {
            if (notify) send(p, "<gray>You weren't in a queue.</gray>");
            return;
        }
        Deque<UUID> q = queuesByKit.get(kit);
        if (q != null) q.remove(p.getUniqueId());
        if (notify) send(p, "<yellow>Left the queue.</yellow>");
    }

    public boolean isQueued(UUID id) { return kitByPlayer.containsKey(id); }
    public @Nullable String queuedKit(UUID id) { return kitByPlayer.get(id); }
    public int queueSize(String kit) {
        Deque<UUID> q = queuesByKit.get(kit.toLowerCase());
        return q == null ? 0 : q.size();
    }

    private void tryMatch(String kitName) {
        Deque<UUID> q = queuesByKit.get(kitName);
        if (q == null || q.size() < 2) return;
        UUID aId = q.pollFirst();
        UUID bId = q.pollFirst();
        kitByPlayer.remove(aId);
        kitByPlayer.remove(bId);
        Player a = Bukkit.getPlayer(aId);
        Player b = Bukkit.getPlayer(bId);
        if (a == null && b == null) return;
        if (a == null) { reEnqueue(b, kitName); return; }
        if (b == null) { reEnqueue(a, kitName); return; }
        // Strip leave-queue items before saveAndPrepare snapshots the inventory.
        removeLeaveBarrier(a);
        removeLeaveBarrier(b);
        // Queue uses default best-of; no time limit (default off, per design).
        runner.start(a, b, kitName, config.defaultFirstTo(), false);
    }

    private void reEnqueue(Player p, String kitName) {
        Deque<UUID> q = queuesByKit.computeIfAbsent(kitName, k -> new ArrayDeque<>());
        q.addFirst(p.getUniqueId());
        kitByPlayer.put(p.getUniqueId(), kitName);
    }

    // ----- leave-queue barrier (in hotbar) -----

    private void giveLeaveBarrier(Player p) {
        if (savedHotbarSlot.containsKey(p.getUniqueId())) return; // already present
        PlayerInventory inv = p.getInventory();
        ItemStack existing = inv.getItem(LEAVE_BARRIER_SLOT);
        if (existing != null && existing.getType() != Material.AIR) {
            savedHotbarSlot.put(p.getUniqueId(), existing.clone());
        } else {
            // Use a marker so we know there was nothing there originally.
            savedHotbarSlot.put(p.getUniqueId(), new ItemStack(Material.AIR));
        }
        inv.setItem(LEAVE_BARRIER_SLOT, buildLeaveBarrier());
    }

    private void removeLeaveBarrier(Player p) {
        if (!savedHotbarSlot.containsKey(p.getUniqueId())) return;
        PlayerInventory inv = p.getInventory();
        // Scan every slot for the tagged barrier and clear it (player may have moved it).
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(leaveQueueKey, PersistentDataType.BYTE)) {
                inv.setItem(i, null);
            }
        }
        ItemStack saved = savedHotbarSlot.remove(p.getUniqueId());
        if (saved != null && saved.getType() != Material.AIR) {
            // Try to restore to slot 8 if empty, otherwise add to inventory (overflow drops at feet).
            if (inv.getItem(LEAVE_BARRIER_SLOT) == null) {
                inv.setItem(LEAVE_BARRIER_SLOT, saved);
            } else {
                Map<Integer, ItemStack> overflow = inv.addItem(saved);
                for (ItemStack drop : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }

    private ItemStack buildLeaveBarrier() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Leave Queue", NamedTextColor.RED, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(Collections.singletonList(
                Component.text("Right-click to leave the queue", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(leaveQueueKey, PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    private void send(Player p, String msg) {
        p.sendMessage(MM.deserialize(config.prefix()).append(MM.deserialize(msg)));
    }
}
