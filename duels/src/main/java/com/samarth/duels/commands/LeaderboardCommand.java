package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import com.samarth.duels.kit.KitsBridge;
import com.samarth.duels.stats.StatsBridge;
import com.samarth.kits.KitService;
import com.samarth.stats.StatsService;
import com.samarth.stats.model.EloEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

/** /elo top &lt;kit&gt; — top 10 by Elo for one kit. */
public final class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final DuelsPlugin plugin;

    public LeaderboardCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        StatsService stats = StatsBridge.tryGet();
        if (stats == null) {
            sender.sendMessage("§cPvPTLStats not loaded — no leaderboard available.");
            return true;
        }
        KitService kits = KitsBridge.tryGet();
        if (args.length < 1) {
            sender.sendMessage("§7Usage: §e/elo top <kit>");
            if (kits != null && !kits.names().isEmpty()) {
                sender.sendMessage("§7Available kits: §f" + String.join(", ", kits.names()));
            }
            return true;
        }
        String sub = args[0].toLowerCase();
        if (!sub.equals("top") && !sub.equals("leaderboard") && !sub.equals("lb")) {
            sender.sendMessage("§7Usage: §e/elo top <kit>");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cYou must specify a kit. Try /elo top <kit>.");
            return true;
        }
        String kit = args[1];
        if (kits != null && kits.get(kit) == null) {
            sender.sendMessage("§cKit '" + kit + "' doesn't exist.");
            return true;
        }
        List<EloEntry> top = stats.topElo(kit, 10);
        if (top.isEmpty()) {
            sender.sendMessage("§7No ranked duels played yet for §f" + kit + "§7.");
            return true;
        }
        sender.sendMessage("§6=== Elo Leaderboard — " + kit + " ===");
        int rank = 0;
        for (EloEntry e : top) {
            rank++;
            String medal = switch (rank) {
                case 1 -> "§e§l#1 ";
                case 2 -> "§7§l#2 ";
                case 3 -> "§6§l#3 ";
                default -> "§8#" + rank + " ";
            };
            sender.sendMessage(medal + "§f" + e.name() + " §8| §bElo " + e.elo()
                + " §8| §a" + e.wins() + "W §c" + e.losses() + "L");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>();
            opts.add("top");
            return opts;
        }
        if (args.length == 2) {
            KitService kits = KitsBridge.tryGet();
            if (kits != null) return new ArrayList<>(kits.names());
        }
        return Collections.emptyList();
    }
}
