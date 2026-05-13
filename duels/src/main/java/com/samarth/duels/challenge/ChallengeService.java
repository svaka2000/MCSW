package com.samarth.duels.challenge;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitsBridge;
import com.samarth.duels.match.MatchRunner;
import com.samarth.kits.KitService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ChallengeService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final DuelsConfig config;
    private final MatchRunner runner;
    private final Map<UUID, Challenge> incoming = new HashMap<>();
    private final Map<UUID, BukkitTask> expirations = new HashMap<>();
    private final Map<UUID, Long> lastChallengeAt = new HashMap<>();

    public ChallengeService(JavaPlugin plugin, DuelsConfig config, MatchRunner runner) {
        this.plugin = plugin;
        this.config = config;
        this.runner = runner;
    }

    public void challenge(Player challenger, Player target, String kitName, int rounds, boolean useTimeLimit) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            send(challenger, "<red>You can't duel yourself.</red>");
            return;
        }
        KitService kits = KitsBridge.tryGet();
        if (kits == null) {
            send(challenger, "<red>PvPTLKits not loaded — duels cannot run.</red>");
            return;
        }
        if (kits.get(kitName) == null) {
            send(challenger, "<red>Kit '" + kitName + "' doesn't exist.</red>");
            return;
        }
        if (runner.isInMatch(challenger.getUniqueId()) || runner.isInMatch(target.getUniqueId())) {
            send(challenger, "<red>One of you is already in a duel.</red>");
            return;
        }
        // Cooldown
        long cooldownMs = (long) config.challengeCooldownSeconds() * 1000L;
        Long last = lastChallengeAt.get(challenger.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownMs) {
            long remainingSec = (cooldownMs - (now - last)) / 1000L + 1L;
            send(challenger, "<red>Wait <yellow>" + remainingSec + "s</yellow> before sending another challenge.</red>");
            return;
        }

        long expiry = now + (long) config.challengeTimeoutSeconds() * 1000L;
        Challenge c = new Challenge(challenger.getUniqueId(), target.getUniqueId(), kitName, rounds, useTimeLimit, expiry);
        incoming.put(target.getUniqueId(), c);
        lastChallengeAt.put(challenger.getUniqueId(), now);

        send(challenger, "<gray>Challenge sent to <yellow>" + target.getName() + "</yellow> — kit <aqua>" + kitName + "</aqua>, BO" + rounds + (useTimeLimit ? ", with time limit" : "") + ".</gray>");

        // Notify target with clickable [ACCEPT] / [DENY] buttons
        Component prefix = MM.deserialize(config.prefix());
        Component msg = Component.text()
            .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
            .append(Component.text(" challenged you to a duel ", NamedTextColor.GRAY))
            .append(Component.text("(", NamedTextColor.DARK_GRAY))
            .append(Component.text(kitName, NamedTextColor.AQUA))
            .append(Component.text(", BO" + rounds + (useTimeLimit ? ", time limit" : ""), NamedTextColor.GRAY))
            .append(Component.text(") ", NamedTextColor.DARK_GRAY))
            .append(Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/duels accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept"))))
            .append(Component.text(" ", NamedTextColor.WHITE))
            .append(Component.text("[DENY]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/duels deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny"))))
            .build();
        target.sendMessage(prefix.append(msg));

        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Challenge cur = incoming.get(target.getUniqueId());
            if (cur == c) {
                incoming.remove(target.getUniqueId());
                Player tp = Bukkit.getPlayer(target.getUniqueId());
                Player cp = Bukkit.getPlayer(challenger.getUniqueId());
                if (tp != null) send(tp, "<gray>Challenge from " + challenger.getName() + " expired.</gray>");
                if (cp != null) send(cp, "<gray>Your challenge to " + target.getName() + " expired.</gray>");
            }
            expirations.remove(target.getUniqueId());
        }, (long) config.challengeTimeoutSeconds() * 20L);
        expirations.put(target.getUniqueId(), t);
    }

    public void accept(Player target) {
        Challenge c = incoming.remove(target.getUniqueId());
        BukkitTask t = expirations.remove(target.getUniqueId());
        if (t != null) t.cancel();
        if (c == null || c.expired()) {
            send(target, "<red>No active challenge for you.</red>");
            return;
        }
        Player challenger = Bukkit.getPlayer(c.challenger());
        if (challenger == null) {
            send(target, "<red>Challenger went offline.</red>");
            return;
        }
        runner.start(challenger, target, c.kitName(), c.rounds(), c.useTimeLimit());
    }

    public void deny(Player target) {
        Challenge c = incoming.remove(target.getUniqueId());
        BukkitTask t = expirations.remove(target.getUniqueId());
        if (t != null) t.cancel();
        if (c == null) {
            send(target, "<gray>You have no pending challenges.</gray>");
            return;
        }
        Player challenger = Bukkit.getPlayer(c.challenger());
        if (challenger != null) send(challenger, "<yellow>" + target.getName() + " denied your duel.</yellow>");
        send(target, "<yellow>Denied.</yellow>");
    }

    private void send(Player p, String msg) {
        p.sendMessage(MM.deserialize(config.prefix()).append(MM.deserialize(msg)));
    }
}
