package com.samarth.parties.listeners;

import com.samarth.parties.PartyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Hands off disconnects so PartyManager can transfer leadership or disband. */
public final class PartyPresenceListener implements Listener {
    private final PartyManager parties;

    public PartyPresenceListener(PartyManager parties) {
        this.parties = parties;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        parties.handleQuit(e.getPlayer().getUniqueId());
    }
}
