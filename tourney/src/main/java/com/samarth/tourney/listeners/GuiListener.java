package com.samarth.tourney.listeners;

import com.samarth.tourney.spectate.SpectatorService;
import com.samarth.tourney.tournament.Match;
import com.samarth.tourney.tournament.Tournament;
import com.samarth.tourney.tournament.TournamentManager;
import com.samarth.tourney.ui.SpectateGui;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class GuiListener implements Listener {
    private final TournamentManager manager;
    private final SpectatorService spec;

    public GuiListener(TournamentManager manager, SpectatorService spec) {
        this.manager = manager;
        this.spec = spec;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Component title = e.getView().title();
        String plain = PlainTextComponentSerializer.plainText().serialize(title);
        if (!"Bracket".equals(plain) && !SpectateGui.TITLE.equals(plain)) return;

        e.setCancelled(true); // no item movement in our GUIs

        if (!(e.getWhoClicked() instanceof Player viewer)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta sm)) return;
        if (sm.getOwningPlayer() == null) return;

        UUID targetId = sm.getOwningPlayer().getUniqueId();
        Match match = manager.findMatchByPlayer(targetId);
        if (match == null) {
            // Player isn't currently in a match — ignore
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) return;

        viewer.closeInventory();
        manager.startSpectating(viewer, target);

        // Localized confirmation
        String msg = "§7You are now spectating §b" + target.getName() + "§7.";
        viewer.sendMessage(msg);
    }
}
