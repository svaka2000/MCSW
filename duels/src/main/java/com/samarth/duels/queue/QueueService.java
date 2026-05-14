package com.samarth.duels.queue;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitsBridge;
import com.samarth.duels.match.MatchRunner;
import com.samarth.kits.KitService;
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

/**
 * Queue manager — owns two parallel sets of per-kit queues:
 *
 *   unrankedByKit  — first-to-{@code default-first-to}, no Elo
 *   rankedByKit    — first-to-{@code ranked-first-to}, Elo-tracked per kit
 *
 * Match selection is FIFO inside a single (kit, ranked) pair. The leave-queue
 * barrier and post-match "Requeue" paper both carry a PDC flag indicating
 * which queue the player came from so right-click re-enqueues into the same
 * bucket.
 */
public final class QueueService {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    /** Hotbar slot used for both the leave-queue barrier and the requeue paper. */
    private static final int LEAVE_BARRIER_SLOT = 8;

    private final JavaPlugin plugin;
    private final DuelsConfig config;
    private final MatchRunner runner;

    private final Map<String, Deque<UUID>> unrankedByKit = new HashMap<>();
    private final Map<String, Deque<UUID>> rankedByKit = new HashMap<>();
    /** Player → (kit, ranked). Tracks which queue a player is currently in. */
    private final Map<UUID, QueueEntry> entryByPlayer = new HashMap<>();

    private final Map<UUID, ItemStack> savedHotbarSlot = new HashMap<>();
    private final Map<UUID, ItemStack> savedRequeueSlot = new HashMap<>();
    private final NamespacedKey leaveQueueKey;
    private final NamespacedKey requeueKey;
    private final NamespacedKey requeueRankedKey;

    public QueueService(JavaPlugin plugin, DuelsConfig config, MatchRunner runner) {
        this.plugin = plugin;
        this.config = config;
        this.runner = runner;
        this.leaveQueueKey = new NamespacedKey(plugin, "leave_queue_item");
        this.requeueKey = new NamespacedKey(plugin, "requeue_kit");
        this.requeueRankedKey = new NamespacedKey(plugin, "requeue_ranked");
    }

    public NamespacedKey leaveQueueKey() { return leaveQueueKey; }
    public NamespacedKey requeueKey() { return requeueKey; }
    public NamespacedKey requeueRankedKey() { return requeueRankedKey; }

    // ----- public enqueue API -----

    public void enqueue(Player p, String kitName) { enqueue(p, kitName, false); }

    /** Add the player to the (kit, ranked) queue. Auto-matches against the head of the same queue. */
    public void enqueue(Player p, String kitName, boolean ranked) {
        if (kitName == null || kitName.isBlank()) {
            send(p, "<red>You must specify a kit.</red>");
            return;
        }
        KitService kits = KitsBridge.tryGet();
        if (kits == null) {
            send(p, "<red>PvPTLKits not loaded — duels cannot run.</red>");
            return;
        }
        if (kits.get(kitName) == null) {
            send(p, "<red>Kit '" + kitName + "' doesn't exist. Use /kitlist to see options.</red>");
            return;
        }
        if (runner.isInMatch(p.getUniqueId())) {
            send(p, "<red>You're already in a duel.</red>");
            return;
        }
        if (entryByPlayer.containsKey(p.getUniqueId())) {
            dequeue(p, false);
        }
        String key = kitName.toLowerCase();
        Map<String, Deque<UUID>> bucket = ranked ? rankedByKit : unrankedByKit;
        Deque<UUID> q = bucket.computeIfAbsent(key, k -> new ArrayDeque<>());
        q.addLast(p.getUniqueId());
        entryByPlayer.put(p.getUniqueId(), new QueueEntry(key, ranked));

        removeRequeueItem(p);
        giveLeaveBarrier(p);

        String tag = ranked ? "<gold>ranked</gold>" : "<gray>unranked</gray>";
        send(p, "<green>Joined " + tag + " queue for kit <yellow>" + kitName
            + "</yellow>. (" + q.size() + " waiting)</green>");
        tryMatch(key, ranked);
    }

    public void dequeue(Player p, boolean notify) {
        QueueEntry entry = entryByPlayer.remove(p.getUniqueId());
        removeLeaveBarrier(p);
        if (entry == null) {
            if (notify) send(p, "<gray>You weren't in a queue.</gray>");
            return;
        }
        Map<String, Deque<UUID>> bucket = entry.ranked ? rankedByKit : unrankedByKit;
        Deque<UUID> q = bucket.get(entry.kit);
        if (q != null) q.remove(p.getUniqueId());
        if (notify) send(p, "<yellow>Left the queue.</yellow>");
    }

    public boolean isQueued(UUID id) { return entryByPlayer.containsKey(id); }
    public @Nullable String queuedKit(UUID id) {
        QueueEntry e = entryByPlayer.get(id);
        return e == null ? null : e.kit;
    }
    public boolean isQueuedRanked(UUID id) {
        QueueEntry e = entryByPlayer.get(id);
        return e != null && e.ranked;
    }

    public int queueSize(String kit, boolean ranked) {
        Deque<UUID> q = (ranked ? rankedByKit : unrankedByKit).get(kit.toLowerCase());
        return q == null ? 0 : q.size();
    }
    /** Back-compat: returns unranked size. */
    public int queueSize(String kit) { return queueSize(kit, false); }

    // ----- matchmaking -----

    private void tryMatch(String kitName, boolean ranked) {
        Map<String, Deque<UUID>> bucket = ranked ? rankedByKit : unrankedByKit;
        Deque<UUID> q = bucket.get(kitName);
        if (q == null || q.size() < 2) return;
        UUID aId = q.pollFirst();
        UUID bId = q.pollFirst();
        entryByPlayer.remove(aId);
        entryByPlayer.remove(bId);
        Player a = Bukkit.getPlayer(aId);
        Player b = Bukkit.getPlayer(bId);
        if (a == null && b == null) return;
        if (a == null) { reEnqueue(b, kitName, ranked); return; }
        if (b == null) { reEnqueue(a, kitName, ranked); return; }
        removeLeaveBarrier(a);
        removeLeaveBarrier(b);
        int firstTo = ranked ? config.rankedFirstTo() : config.defaultFirstTo();
        runner.start(a, b, kitName, firstTo, false, ranked);
    }

    private void reEnqueue(Player p, String kitName, boolean ranked) {
        Map<String, Deque<UUID>> bucket = ranked ? rankedByKit : unrankedByKit;
        Deque<UUID> q = bucket.computeIfAbsent(kitName, k -> new ArrayDeque<>());
        q.addFirst(p.getUniqueId());
        entryByPlayer.put(p.getUniqueId(), new QueueEntry(kitName, ranked));
    }

    // ----- leave-queue barrier -----

    private void giveLeaveBarrier(Player p) {
        if (savedHotbarSlot.containsKey(p.getUniqueId())) return;
        PlayerInventory inv = p.getInventory();
        ItemStack existing = inv.getItem(LEAVE_BARRIER_SLOT);
        if (existing != null && existing.getType() != Material.AIR) {
            savedHotbarSlot.put(p.getUniqueId(), existing.clone());
        } else {
            savedHotbarSlot.put(p.getUniqueId(), new ItemStack(Material.AIR));
        }
        inv.setItem(LEAVE_BARRIER_SLOT, buildLeaveBarrier());
    }

    private void removeLeaveBarrier(Player p) {
        if (!savedHotbarSlot.containsKey(p.getUniqueId())) return;
        PlayerInventory inv = p.getInventory();
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
            if (inv.getItem(LEAVE_BARRIER_SLOT) == null) {
                inv.setItem(LEAVE_BARRIER_SLOT, saved);
            } else {
                for (ItemStack drop : inv.addItem(saved).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }

    // ----- requeue paper -----

    public void giveRequeueItem(Player p, String kitName) {
        giveRequeueItem(p, kitName, false);
    }

    /**
     * Place a "Requeue: <kit>" paper in the player's slot 8. The PDC carries the
     * kit name AND the ranked flag, so right-click re-enqueues into the same bucket
     * the player just played from.
     */
    public void giveRequeueItem(Player p, String kitName, boolean ranked) {
        removeRequeueItem(p);
        PlayerInventory inv = p.getInventory();
        ItemStack existing = inv.getItem(LEAVE_BARRIER_SLOT);
        if (existing != null && existing.getType() != Material.AIR) {
            savedRequeueSlot.put(p.getUniqueId(), existing.clone());
        } else {
            savedRequeueSlot.put(p.getUniqueId(), new ItemStack(Material.AIR));
        }
        inv.setItem(LEAVE_BARRIER_SLOT, buildRequeueItem(kitName, ranked));
    }

    public void removeRequeueItem(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(requeueKey, PersistentDataType.STRING)) {
                inv.setItem(i, null);
            }
        }
        ItemStack saved = savedRequeueSlot.remove(p.getUniqueId());
        if (saved != null && saved.getType() != Material.AIR) {
            if (inv.getItem(LEAVE_BARRIER_SLOT) == null) {
                inv.setItem(LEAVE_BARRIER_SLOT, saved);
            } else {
                for (ItemStack drop : inv.addItem(saved).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }

    private ItemStack buildRequeueItem(String kitName, boolean ranked) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String label = ranked ? "Requeue: " + kitName + " §6(Ranked)" : "Requeue: " + kitName;
            meta.displayName(Component.text(label,
                ranked ? NamedTextColor.GOLD : NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.Arrays.asList(
                Component.text("Right-click to queue for "
                    + kitName + (ranked ? " (ranked)" : "") + " again", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("Disappears when you start another duel",
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            var pdc = meta.getPersistentDataContainer();
            pdc.set(requeueKey, PersistentDataType.STRING, kitName);
            pdc.set(requeueRankedKey, PersistentDataType.BYTE, (byte) (ranked ? 1 : 0));
            it.setItemMeta(meta);
        }
        return it;
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

    private record QueueEntry(String kit, boolean ranked) {}
}
