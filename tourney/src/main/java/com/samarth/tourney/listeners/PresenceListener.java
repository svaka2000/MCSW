package com.samarth.tourney.listeners;

import com.samarth.tourney.persistence.StateStore;
import com.samarth.tourney.tournament.TournamentManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PresenceListener implements Listener {
    private final TournamentManager manager;
    private final StateStore store;

    public PresenceListener(TournamentManager manager, StateStore store) {
        this.manager = manager;
        this.store = store;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // First, see if there's any leftover state from a crash before any active-match logic
        // (only kick in if not currently in an active match grace period).
        boolean restored = false;
        if (manager.matchOf(e.getPlayer().getUniqueId()) == null) {
            restored = store.restoreIfPresent(e.getPlayer());
        }
        manager.onPlayerJoin(e.getPlayer());
        if (restored) {
            e.getPlayer().sendMessage("§7[Tourney] Restored your inventory from a previous session.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.onPlayerQuit(e.getPlayer());
    }
}
