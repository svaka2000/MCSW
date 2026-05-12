package com.samarth.stats.listeners;

import com.samarth.stats.StatsService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Touches the `players` row on every join so we always have current name + last_seen. */
public final class PresenceListener implements Listener {
    private final StatsService stats;

    public PresenceListener(StatsService stats) {
        this.stats = stats;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        stats.touchPlayer(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }
}
