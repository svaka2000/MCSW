package com.samarth.tourney.ui;

import com.samarth.tourney.tournament.Bracket;
import com.samarth.tourney.tournament.BracketMatch;
import com.samarth.tourney.tournament.Match;
import com.samarth.tourney.tournament.Tournament;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class BracketGui {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public static Inventory build(Tournament tournament) {
        Bracket bracket = tournament.bracket();
        int rows = Math.min(6, Math.max(3, bracket == null ? 3 : bracket.totalRounds() + 1));
        Inventory inv = Bukkit.createInventory(null, rows * 9, Component.text("Bracket", NamedTextColor.GOLD));
        if (bracket == null) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta m = barrier.getItemMeta();
            if (m != null) {
                m.displayName(Component.text("No bracket yet", NamedTextColor.RED));
                barrier.setItemMeta(m);
            }
            inv.setItem(rows * 9 / 2, barrier);
            return inv;
        }

        Set<UUID> eliminated = collectEliminated(bracket);
        Set<UUID> playing = collectPlaying(tournament);

        // Layout: each round is a column. Round 0 leftmost.
        int totalRounds = bracket.totalRounds();
        for (int r = 0; r < totalRounds && r < 9; r++) {
            List<BracketMatch> round = bracket.rounds().get(r);
            int col = r;
            int matchCount = round.size();
            int rowsToUse = rows;
            // distribute matches vertically — center them
            for (int i = 0; i < matchCount; i++) {
                BracketMatch bm = round.get(i);
                int row = (rowsToUse * (i + 1)) / (matchCount + 1);
                row = Math.max(0, Math.min(rowsToUse - 1, row));
                int slot = row * 9 + col;
                inv.setItem(slot, headForMatch(bm, eliminated, playing));
            }
        }
        return inv;
    }

    private static ItemStack headForMatch(BracketMatch bm, Set<UUID> eliminated, Set<UUID> playing) {
        if (bm.played() && bm.winner() != null) {
            return playerHead(bm.winner(), bm, eliminated, playing);
        }
        if (bm.playerA() != null && bm.playerB() != null) {
            // Both filled, not yet played — show the "current matchup" head as player A
            return playerHead(bm.playerA(), bm, eliminated, playing);
        }
        if (bm.playerA() != null) return playerHead(bm.playerA(), bm, eliminated, playing);
        if (bm.playerB() != null) return playerHead(bm.playerB(), bm, eliminated, playing);
        // Empty slot
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = glass.getItemMeta();
        if (m != null) {
            m.displayName(Component.text("TBD", NamedTextColor.DARK_GRAY));
            glass.setItemMeta(m);
        }
        return glass;
    }

    private static ItemStack playerHead(UUID id, BracketMatch bm, Set<UUID> eliminated, Set<UUID> playing) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        if (meta != null) {
            meta.setOwningPlayer(op);
            String name = op.getName() == null ? "?" : op.getName();
            NamedTextColor color = NamedTextColor.WHITE;
            String status;
            if (eliminated.contains(id)) {
                color = NamedTextColor.RED;
                status = "Eliminated";
            } else if (playing.contains(id)) {
                color = NamedTextColor.GOLD;
                status = "In Match";
            } else if (bm.played() && id.equals(bm.winner())) {
                color = NamedTextColor.GREEN;
                status = "Advanced";
            } else {
                status = "Waiting";
            }
            meta.displayName(Component.text(name, color));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Round " + (bm.round() + 1), NamedTextColor.GRAY));
            lore.add(Component.text(status, color));
            if (bm.played() && bm.playerA() != null && bm.playerB() != null) {
                String opponent = id.equals(bm.playerA())
                    ? Bukkit.getOfflinePlayer(bm.playerB()).getName()
                    : Bukkit.getOfflinePlayer(bm.playerA()).getName();
                if (opponent != null) {
                    lore.add(Component.text("vs " + opponent, NamedTextColor.DARK_GRAY));
                }
            }
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static Set<UUID> collectEliminated(Bracket bracket) {
        Set<UUID> result = new HashSet<>();
        for (List<BracketMatch> round : bracket.rounds()) {
            for (BracketMatch bm : round) {
                if (bm.played() && bm.winner() != null) {
                    if (bm.playerA() != null && !bm.playerA().equals(bm.winner())) result.add(bm.playerA());
                    if (bm.playerB() != null && !bm.playerB().equals(bm.winner())) result.add(bm.playerB());
                }
            }
        }
        return result;
    }

    private static Set<UUID> collectPlaying(Tournament tournament) {
        Set<UUID> result = new HashSet<>();
        for (Match m : tournament.activeMatchesByArena().values()) {
            result.add(m.playerA());
            result.add(m.playerB());
        }
        return result;
    }

    public static void open(Player p, Tournament t) {
        p.openInventory(build(t));
    }
}
