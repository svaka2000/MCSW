package com.samarth.stats.commands;

import com.samarth.stats.StatsService;
import com.samarth.stats.model.LeaderboardEntry;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class TopCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final StatsService stats;

    public TopCommand(JavaPlugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String category = args[0].toLowerCase();
        int limit = plugin.getConfig().getInt("leaderboard.default-limit", 10);
        int maxLimit = plugin.getConfig().getInt("leaderboard.max-limit", 50);
        if (args.length >= 2) {
            try { limit = Math.max(1, Math.min(maxLimit, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {}
        }
        final int n = limit;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<LeaderboardEntry> entries;
            String title;
            String unit;
            switch (category) {
                case "duel-wins", "duels", "wins" -> { entries = stats.topDuelWins(n); title = "Top Duel Wins"; unit = "wins"; }
                case "tourney-wins", "tourney", "tournaments" -> { entries = stats.topTournamentWins(n); title = "Top Tournament Wins"; unit = "championships"; }
                case "kd", "win-rate", "winrate" -> { entries = stats.topWinRate(n); title = "Top Win Rate"; unit = "%"; }
                default -> {
                    if (args.length >= 2 || category.startsWith("kit:")) {
                        String kit = category.startsWith("kit:") ? category.substring(4) : category;
                        entries = stats.topByKit(kit, n);
                        title = "Top " + kit;
                        unit = "wins";
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> sendHelp(sender));
                        return;
                    }
                }
            }
            final List<LeaderboardEntry> resolved = entries;
            final String t = title, u = unit;
            Bukkit.getScheduler().runTask(plugin, () -> render(sender, t, u, resolved));
        });
        return true;
    }

    private void render(CommandSender sender, String title, String unit, List<LeaderboardEntry> entries) {
        sender.sendMessage("§6=== " + title + " ===");
        if (entries.isEmpty()) {
            sender.sendMessage("§7No data yet.");
            return;
        }
        for (LeaderboardEntry e : entries) {
            String score = unit.equals("%") ? String.format("%.0f%%", e.score() * 100)
                                            : String.format("%.0f " + unit, e.score());
            sender.sendMessage("§7 " + e.rank() + ". §f" + e.name() + "  §7— §e" + score);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== /top ===");
        sender.sendMessage("§e/top duel-wins §7— most duel wins");
        sender.sendMessage("§e/top tourney-wins §7— most tournament championships");
        sender.sendMessage("§e/top kd §7— highest win rate (5+ duels)");
        sender.sendMessage("§e/top <kit-name> §7— top wins on a specific kit");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("duel-wins", "tourney-wins", "kd");
        }
        return List.of();
    }
}
