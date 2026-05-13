package com.samarth.duels.challenge;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitsBridge;
import com.samarth.duels.match.MatchRunner;
import com.samarth.duels.parties.PartiesBridge;
import com.samarth.kits.KitService;
import com.samarth.parties.Party;
import com.samarth.parties.PartyService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        sendChallenge(challenger, target, kitName, rounds, useTimeLimit, false);
    }

    /**
     * Party-vs-party challenge. {@code challenger} and {@code target} must each be the leader of
     * a party. Actual rosters are resolved when the target accepts (so members joining/leaving
     * between challenge and accept is handled gracefully).
     */
    public void partyChallenge(Player challenger, Player target, String kitName, int rounds, boolean useTimeLimit) {
        PartyService parties = PartiesBridge.tryGet();
        if (parties == null) {
            send(challenger, "<red>PvPTLParties not loaded — party duels unavailable.</red>");
            return;
        }
        Party cp = parties.partyOf(challenger.getUniqueId());
        if (cp == null || !cp.isLeader(challenger.getUniqueId())) {
            send(challenger, "<red>You must be a party leader to send a party challenge. Run /party create first.</red>");
            return;
        }
        Party tp = parties.partyOf(target.getUniqueId());
        if (tp == null || !tp.isLeader(target.getUniqueId())) {
            send(challenger, "<red>" + target.getName() + " is not a party leader.</red>");
            return;
        }
        if (cp.id().equals(tp.id())) {
            send(challenger, "<red>You're both in the same party — can't duel yourselves.</red>");
            return;
        }
        int maxSize = config.maxTeamSize();
        if (cp.size() > maxSize || tp.size() > maxSize) {
            send(challenger, "<red>Arena only supports up to " + maxSize + "v" + maxSize
                + " — your party has " + cp.size() + ", theirs has " + tp.size() + ".</red>");
            return;
        }
        sendChallenge(challenger, target, kitName, rounds, useTimeLimit, true);
    }

    private void sendChallenge(Player challenger, Player target, String kitName,
                               int rounds, boolean useTimeLimit, boolean party) {
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
        long cooldownMs = (long) config.challengeCooldownSeconds() * 1000L;
        Long last = lastChallengeAt.get(challenger.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < cooldownMs) {
            long remainingSec = (cooldownMs - (now - last)) / 1000L + 1L;
            send(challenger, "<red>Wait <yellow>" + remainingSec + "s</yellow> before sending another challenge.</red>");
            return;
        }

        long expiry = now + (long) config.challengeTimeoutSeconds() * 1000L;
        Challenge c = new Challenge(challenger.getUniqueId(), target.getUniqueId(),
            kitName, rounds, useTimeLimit, expiry, party);
        incoming.put(target.getUniqueId(), c);
        lastChallengeAt.put(challenger.getUniqueId(), now);

        String label = party ? "party duel" : "duel";
        send(challenger, "<gray>Challenge sent to <yellow>" + target.getName()
            + "</yellow> — kit <aqua>" + kitName + "</aqua>, first to " + rounds
            + (useTimeLimit ? ", with time limit" : "") + (party ? " <gold>(party)</gold>" : "") + ".</gray>");

        Component prefix = MM.deserialize(config.prefix());
        Component msg = Component.text()
            .append(Component.text(challenger.getName(), NamedTextColor.GOLD))
            .append(Component.text(" challenged you to a " + label + " ", NamedTextColor.GRAY))
            .append(Component.text("(", NamedTextColor.DARK_GRAY))
            .append(Component.text(kitName, NamedTextColor.AQUA))
            .append(Component.text(", first to " + rounds + (useTimeLimit ? ", time limit" : ""), NamedTextColor.GRAY))
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
        if (c.party()) {
            acceptParty(target, challenger, c);
        } else {
            runner.start(challenger, target, c.kitName(), c.rounds(), c.useTimeLimit());
        }
    }

    private void acceptParty(Player target, Player challenger, Challenge c) {
        PartyService parties = PartiesBridge.tryGet();
        if (parties == null) {
            send(target, "<red>PvPTLParties is no longer loaded — can't start a party duel.</red>");
            return;
        }
        Party cp = parties.partyOf(challenger.getUniqueId());
        Party tp = parties.partyOf(target.getUniqueId());
        if (cp == null || !cp.isLeader(challenger.getUniqueId())) {
            send(target, "<red>Challenger is no longer a party leader.</red>");
            send(challenger, "<red>You're no longer leading a party — challenge cancelled.</red>");
            return;
        }
        if (tp == null || !tp.isLeader(target.getUniqueId())) {
            send(target, "<red>You're no longer a party leader — challenge cancelled.</red>");
            send(challenger, "<red>" + target.getName() + " is no longer a party leader.</red>");
            return;
        }
        if (cp.id().equals(tp.id())) {
            send(target, "<red>You can't duel your own party.</red>");
            return;
        }
        List<Player> teamA = resolveOnlineMembers(cp);
        List<Player> teamB = resolveOnlineMembers(tp);
        if (teamA.size() != cp.size()) {
            send(target, "<red>Challenger's party has offline members — can't start.</red>");
            send(challenger, "<red>One of your party members is offline — match cancelled.</red>");
            return;
        }
        if (teamB.size() != tp.size()) {
            send(target, "<red>Your party has offline members — can't start.</red>");
            send(challenger, "<red>" + target.getName() + "'s party has offline members — match cancelled.</red>");
            return;
        }
        for (Player p : teamA) {
            if (runner.isInMatch(p.getUniqueId())) {
                send(target, "<red>" + p.getName() + " is already in a duel.</red>");
                send(challenger, "<red>" + p.getName() + " is already in a duel.</red>");
                return;
            }
        }
        for (Player p : teamB) {
            if (runner.isInMatch(p.getUniqueId())) {
                send(target, "<red>" + p.getName() + " is already in a duel.</red>");
                send(challenger, "<red>" + p.getName() + " is already in a duel.</red>");
                return;
            }
        }
        runner.start(teamA, teamB, c.kitName(), c.rounds(), c.useTimeLimit());
    }

    private static List<Player> resolveOnlineMembers(Party party) {
        // Leader first (so they get slot 0 — the arena's "primary" spawn), then the rest.
        List<Player> out = new ArrayList<>(party.size());
        Player leader = Bukkit.getPlayer(party.leader());
        if (leader != null) out.add(leader);
        for (UUID id : party.members()) {
            if (id.equals(party.leader())) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
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
