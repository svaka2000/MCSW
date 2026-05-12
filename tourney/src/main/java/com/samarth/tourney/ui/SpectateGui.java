package com.samarth.tourney.ui;

import com.samarth.tourney.tournament.Match;
import com.samarth.tourney.tournament.Tournament;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class SpectateGui {
    public static final String TITLE = "Live Matches";

    public static Inventory build(Tournament tournament) {
        List<Match> matches = new ArrayList<>(tournament.activeMatchesByArena().values());
        int slots = Math.max(9, ((matches.size() / 9) + (matches.size() % 9 == 0 ? 0 : 1)) * 9);
        if (slots > 54) slots = 54;
        Inventory inv = Bukkit.createInventory(null, slots, Component.text(TITLE, NamedTextColor.GOLD));
        for (int i = 0; i < matches.size() && i < slots; i++) {
            Match m = matches.get(i);
            inv.setItem(i, matchHead(m));
        }
        return inv;
    }

    private static ItemStack matchHead(Match m) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer a = Bukkit.getOfflinePlayer(m.playerA());
        OfflinePlayer b = Bukkit.getOfflinePlayer(m.playerB());
        if (meta != null) {
            meta.setOwningPlayer(a);
            String an = a.getName() == null ? "?" : a.getName();
            String bn = b.getName() == null ? "?" : b.getName();
            meta.displayName(Component.text(an + " vs " + bn, NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Arena: " + m.arena().name(), NamedTextColor.GRAY));
            lore.add(Component.text(an + ": " + m.killsA(), NamedTextColor.AQUA));
            lore.add(Component.text(bn + ": " + m.killsB(), NamedTextColor.RED));
            lore.add(Component.empty());
            lore.add(Component.text("Click to spectate", NamedTextColor.GREEN));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    public static void open(Player p, Tournament t) {
        p.openInventory(build(t));
    }
}
