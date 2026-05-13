package com.samarth.parties;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/** Default {@link PartyService} implementation — in-memory, session-scoped. */
public final class PartyManager implements PartyService {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final Map<UUID, Party> partyByPlayer = new HashMap<>();
    private final Map<UUID, Party> partyById = new HashMap<>();
    /** target → invite */
    private final Map<UUID, PendingInvite> pendingInvites = new HashMap<>();

    public PartyManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @Nullable
    public synchronized Party create(Player leader) {
        if (partyByPlayer.containsKey(leader.getUniqueId())) {
            send(leader, "<red>You're already in a party. Leave it first.</red>");
            return null;
        }
        Party p = new Party(leader.getUniqueId());
        partyByPlayer.put(leader.getUniqueId(), p);
        partyById.put(p.id(), p);
        send(leader, "<green>Party created. Use </green><yellow>/party invite <player></yellow><green> to bring friends.</green>");
        return p;
    }

    @Override
    @Nullable
    public synchronized Party partyOf(UUID id) {
        return partyByPlayer.get(id);
    }

    @Override
    public synchronized boolean invite(Player from, Player to) {
        if (from.equals(to)) {
            send(from, "<red>Invite yourself? Bold.</red>");
            return false;
        }
        Party fromParty = partyByPlayer.get(from.getUniqueId());
        if (fromParty == null) {
            // Auto-create a party for the inviter
            fromParty = create(from);
            if (fromParty == null) return false;
        } else if (!fromParty.isLeader(from.getUniqueId())) {
            send(from, "<red>Only the party leader can invite.</red>");
            return false;
        }
        if (partyByPlayer.containsKey(to.getUniqueId())) {
            send(from, "<red>" + to.getName() + " is already in a party.</red>");
            return false;
        }
        int maxSize = plugin.getConfig().getInt("party.max-size", 8);
        if (fromParty.size() >= maxSize) {
            send(from, "<red>Party is full (" + maxSize + ").</red>");
            return false;
        }

        // Cancel previous pending invite for this target
        PendingInvite existing = pendingInvites.remove(to.getUniqueId());
        if (existing != null && existing.timeoutTask != null) existing.timeoutTask.cancel();

        long timeoutSec = plugin.getConfig().getLong("party.invite-timeout-seconds", 60);
        final UUID partyId = fromParty.id();      // effectively-final captures for the lambda
        final UUID toId = to.getUniqueId();
        final UUID fromId = from.getUniqueId();
        final String fromName = from.getName();
        final String toName = to.getName();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            synchronized (this) {
                PendingInvite cur = pendingInvites.get(toId);
                if (cur != null && cur.fromParty.equals(partyId)) {
                    pendingInvites.remove(toId);
                    Player tp = Bukkit.getPlayer(toId);
                    Player fp = Bukkit.getPlayer(fromId);
                    if (tp != null) send(tp, "<gray>Party invite from " + fromName + " expired.</gray>");
                    if (fp != null) send(fp, "<gray>Your party invite to " + toName + " expired.</gray>");
                }
            }
        }, timeoutSec * 20L);
        pendingInvites.put(to.getUniqueId(), new PendingInvite(partyId, fromId, task));

        send(from, "<gray>Invited <yellow>" + to.getName() + "</yellow> to your party.</gray>");

        Component prefix = MM.deserialize(plugin.getConfig().getString("messages.prefix", "[Party] "));
        Component msg = Component.text()
            .append(Component.text(from.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" invited you to their party. ", NamedTextColor.GRAY))
            .append(Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/party accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept"))))
            .append(Component.text("  ", NamedTextColor.WHITE))
            .append(Component.text("[DENY]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/party deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny"))))
            .build();
        to.sendMessage(prefix.append(msg));
        return true;
    }

    @Override
    @Nullable
    public synchronized Party accept(Player target) {
        PendingInvite inv = pendingInvites.remove(target.getUniqueId());
        if (inv == null) {
            send(target, "<red>No pending party invite.</red>");
            return null;
        }
        if (inv.timeoutTask != null) inv.timeoutTask.cancel();
        Party party = partyById.get(inv.fromParty);
        if (party == null) {
            send(target, "<red>That party no longer exists.</red>");
            return null;
        }
        if (partyByPlayer.containsKey(target.getUniqueId())) {
            send(target, "<red>You're already in another party.</red>");
            return null;
        }
        int maxSize = plugin.getConfig().getInt("party.max-size", 8);
        if (party.size() >= maxSize) {
            send(target, "<red>That party is full.</red>");
            return null;
        }
        party.addMember(target.getUniqueId());
        partyByPlayer.put(target.getUniqueId(), party);
        broadcastToParty(party, "<aqua>" + target.getName() + "</aqua><gray> joined the party. </gray><dark_gray>(" + party.size() + " members)</dark_gray>");
        return party;
    }

    @Override
    public synchronized boolean deny(Player target) {
        PendingInvite inv = pendingInvites.remove(target.getUniqueId());
        if (inv == null) {
            send(target, "<gray>You have no pending party invites.</gray>");
            return false;
        }
        if (inv.timeoutTask != null) inv.timeoutTask.cancel();
        Player from = Bukkit.getPlayer(inv.fromPlayer);
        if (from != null) send(from, "<yellow>" + target.getName() + " denied your party invite.</yellow>");
        send(target, "<yellow>Denied.</yellow>");
        return true;
    }

    @Override
    public synchronized boolean leave(Player p) {
        Party party = partyByPlayer.remove(p.getUniqueId());
        if (party == null) {
            send(p, "<gray>You're not in a party.</gray>");
            return false;
        }
        boolean wasLeader = party.isLeader(p.getUniqueId());
        party.removeMember(p.getUniqueId());
        send(p, "<yellow>You left the party.</yellow>");
        if (party.size() == 0) {
            partyById.remove(party.id());
            return true;
        }
        broadcastToParty(party, "<yellow>" + p.getName() + "</yellow><gray> left the party.</gray>");
        if (wasLeader) {
            String newLeaderName = Bukkit.getOfflinePlayer(party.leader()).getName();
            broadcastToParty(party, "<gray>Leadership transferred to <aqua>" + newLeaderName + "</aqua>.</gray>");
        }
        return true;
    }

    @Override
    public synchronized boolean disband(Player leader) {
        Party party = partyByPlayer.get(leader.getUniqueId());
        if (party == null) {
            send(leader, "<gray>You're not in a party.</gray>");
            return false;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            send(leader, "<red>Only the leader can disband.</red>");
            return false;
        }
        broadcastToParty(party, "<red>Party disbanded by " + leader.getName() + ".</red>");
        for (UUID id : party.members()) partyByPlayer.remove(id);
        partyById.remove(party.id());
        return true;
    }

    @Override
    public synchronized boolean kick(Player leader, Player target) {
        Party party = partyByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            send(leader, "<red>Only the leader can kick.</red>");
            return false;
        }
        if (!party.contains(target.getUniqueId())) {
            send(leader, "<red>" + target.getName() + " isn't in your party.</red>");
            return false;
        }
        if (target.getUniqueId().equals(leader.getUniqueId())) {
            send(leader, "<red>You can't kick yourself. Use /party disband or /party leave.</red>");
            return false;
        }
        party.removeMember(target.getUniqueId());
        partyByPlayer.remove(target.getUniqueId());
        broadcastToParty(party, "<red>" + target.getName() + " was kicked from the party.</red>");
        send(target, "<red>You were kicked from " + leader.getName() + "'s party.</red>");
        return true;
    }

    @Override
    public synchronized boolean promote(Player leader, Player newLeader) {
        Party party = partyByPlayer.get(leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            send(leader, "<red>Only the current leader can promote.</red>");
            return false;
        }
        if (!party.contains(newLeader.getUniqueId())) {
            send(leader, "<red>" + newLeader.getName() + " isn't in your party.</red>");
            return false;
        }
        if (newLeader.getUniqueId().equals(leader.getUniqueId())) {
            send(leader, "<gray>You're already the leader.</gray>");
            return false;
        }
        party.promote(newLeader.getUniqueId());
        broadcastToParty(party, "<gold>" + newLeader.getName() + "</gold><gray> is now the party leader.</gray>");
        return true;
    }

    @Override
    public synchronized void partyChat(Player from, String message) {
        Party party = partyByPlayer.get(from.getUniqueId());
        if (party == null) {
            send(from, "<red>You're not in a party.</red>");
            return;
        }
        String fmt = plugin.getConfig().getString("messages.chat-format",
            "<gray>[Party]</gray> <yellow><name></yellow>: <white><message></white>");
        Component line = MM.deserialize(fmt,
            Placeholder.parsed("name", from.getName()),
            Placeholder.parsed("message", message));
        for (UUID id : party.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(line);
        }
    }

    @Override
    public synchronized Collection<Party> allParties() {
        return Collections.unmodifiableCollection(new LinkedHashMap<>(partyById).values());
    }

    @Override
    public synchronized boolean hasPendingInvite(UUID target) {
        return pendingInvites.containsKey(target);
    }

    @Override
    @Nullable
    public synchronized UUID inviterOf(UUID target) {
        PendingInvite inv = pendingInvites.get(target);
        return inv == null ? null : inv.fromPlayer;
    }

    /** Called by PartyPresenceListener when a member quits the server. */
    public synchronized void handleQuit(UUID id) {
        Party party = partyByPlayer.get(id);
        if (party == null) return;
        boolean disbandOnLeaderQuit = plugin.getConfig().getBoolean("party.disband-on-leader-quit", false);
        if (party.isLeader(id) && disbandOnLeaderQuit) {
            for (UUID m : party.members()) partyByPlayer.remove(m);
            partyById.remove(party.id());
            broadcastToParty(party, "<red>The party disbanded — the leader disconnected.</red>");
            return;
        }
        boolean wasLeader = party.isLeader(id);
        party.removeMember(id);
        partyByPlayer.remove(id);
        if (party.size() == 0) {
            partyById.remove(party.id());
            return;
        }
        String name = Bukkit.getOfflinePlayer(id).getName();
        broadcastToParty(party, "<gray><yellow>" + name + "</yellow> disconnected.</gray>");
        if (wasLeader) {
            String newLeaderName = Bukkit.getOfflinePlayer(party.leader()).getName();
            broadcastToParty(party, "<gray>Leadership transferred to <aqua>" + newLeaderName + "</aqua>.</gray>");
        }
    }

    // ----- helpers -----

    private void broadcastToParty(Party party, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "[Party] ");
        Component line = MM.deserialize(prefix).append(MM.deserialize(message));
        for (UUID id : party.members()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(line);
        }
    }

    private void send(Player p, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "[Party] ");
        p.sendMessage(MM.deserialize(prefix).append(MM.deserialize(message)));
    }

    private static final class PendingInvite {
        final UUID fromParty;
        final UUID fromPlayer;
        @Nullable final BukkitTask timeoutTask;
        PendingInvite(UUID fromParty, UUID fromPlayer, @Nullable BukkitTask timeoutTask) {
            this.fromParty = fromParty;
            this.fromPlayer = fromPlayer;
            this.timeoutTask = timeoutTask;
        }
    }
}
