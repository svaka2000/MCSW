package com.samarth.duels.queue;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitRegistry;
import com.samarth.duels.match.MatchRunner;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class QueueService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final DuelsConfig config;
    private final KitRegistry kits;
    private final MatchRunner runner;

    private final Map<String, Deque<UUID>> queuesByKit = new HashMap<>();
    private final Map<UUID, String> kitByPlayer = new HashMap<>();

    public QueueService(DuelsConfig config, KitRegistry kits, MatchRunner runner) {
        this.config = config;
        this.kits = kits;
        this.runner = runner;
    }

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

        send(p, config.msg("queued")
            .replace("<kit>", kitName)
            .replace("<count>", String.valueOf(q.size())));
        tryMatch(kitName.toLowerCase());
    }

    public void dequeue(Player p, boolean notify) {
        String kit = kitByPlayer.remove(p.getUniqueId());
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
        // Queue uses default best-of; no time limit (default off, per design).
        runner.start(a, b, kitName, config.defaultBestOf(), false);
    }

    private void reEnqueue(Player p, String kitName) {
        Deque<UUID> q = queuesByKit.computeIfAbsent(kitName, k -> new ArrayDeque<>());
        q.addFirst(p.getUniqueId());
        kitByPlayer.put(p.getUniqueId(), kitName);
    }

    private void send(Player p, String msg) {
        p.sendMessage(MM.deserialize(config.prefix()).append(MM.deserialize(msg)));
    }
}
