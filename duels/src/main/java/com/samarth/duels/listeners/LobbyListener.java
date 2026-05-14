package com.samarth.duels.listeners;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.lobby.LobbyItems;
import com.samarth.duels.match.MatchRunner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Gives the diamond/iron sword lobby pickers whenever a player lands in the lobby:
 *
 *   - PlayerJoinEvent — if a lobby is configured, teleport new players to it and give items
 *   - PlayerTeleportEvent — if the destination is at the lobby location, give items (post-match)
 *   - PlayerRespawnEvent — fallback for dead loser respawn path
 *
 * MatchRunner strips these in {@code saveAndPrepare} before each duel, so they
 * don't get snapshotted into the player's pre-duel inventory.
 */
public final class LobbyListener implements Listener {
    /** Squared block radius around the lobby that still counts as "in the lobby." */
    private static final double LOBBY_RADIUS_SQ = 16.0 * 16.0;

    private final JavaPlugin plugin;
    private final DuelsConfig config;
    private final MatchRunner matches;
    private final LobbyItems lobbyItems;

    public LobbyListener(JavaPlugin plugin, DuelsConfig config,
                         MatchRunner matches, LobbyItems lobbyItems) {
        this.plugin = plugin;
        this.config = config;
        this.matches = matches;
        this.lobbyItems = lobbyItems;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!config.lobbyItemsEnabled()) return;
        Player p = e.getPlayer();
        Location lobby = config.lobby();
        if (lobby == null) return;
        // Teleport to lobby on join — and give items 1 tick later so the post-join
        // inventory restore (other plugins) doesn't clobber them.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (matches.isInMatch(p.getUniqueId())) return;
            p.teleport(lobby);
            lobbyItems.give(p);
        }, 5L);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (!config.lobbyItemsEnabled()) return;
        Player p = e.getPlayer();
        if (matches.isInMatch(p.getUniqueId())) return;
        Location lobby = config.lobby();
        Location dest = e.getTo();
        if (lobby == null || dest == null) return;
        if (!dest.getWorld().equals(lobby.getWorld())) return;
        if (dest.distanceSquared(lobby) > LOBBY_RADIUS_SQ) return;
        // Slight delay so any inventory restore that fires alongside the teleport
        // (post-match finalizePlayer) completes first.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !matches.isInMatch(p.getUniqueId())) lobbyItems.give(p);
        }, 3L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!config.lobbyItemsEnabled()) return;
        Player p = e.getPlayer();
        Location lobby = config.lobby();
        Location dest = e.getRespawnLocation();
        if (lobby == null || dest == null) return;
        if (!dest.getWorld().equals(lobby.getWorld())) return;
        if (dest.distanceSquared(lobby) > LOBBY_RADIUS_SQ) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !matches.isInMatch(p.getUniqueId())) lobbyItems.give(p);
        }, 3L);
    }
}
