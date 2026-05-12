package com.samarth.stats.commands;

import com.samarth.stats.StatsService;
import com.samarth.stats.model.KitStats;
import com.samarth.stats.model.PlayerProfile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class StatsCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final StatsService stats;

    public StatsCommand(JavaPlugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        UUID target;
        String displayName;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cUsage: /stats <player>");
                return true;
            }
            target = p.getUniqueId();
            displayName = p.getName();
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (op == null) op = Bukkit.getOfflinePlayer(args[0]);
            target = op.getUniqueId();
            displayName = op.getName() == null ? args[0] : op.getName();
        }
        final String name = displayName;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile = stats.getProfile(target);
            Bukkit.getScheduler().runTask(plugin, () -> render(sender, profile, name));
        });
        return true;
    }

    private void render(CommandSender sender, PlayerProfile profile, String fallbackName) {
        if (profile == null) {
            sender.sendMessage("§7No stats recorded for §f" + fallbackName + "§7 yet.");
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        sender.sendMessage("§6=== Stats: §f" + profile.name() + " §6===");
        sender.sendMessage("§7Duels: §f" + profile.duelWins() + " §7W / §f" + profile.duelLosses()
            + " §7L  §8(§7" + String.format("%.0f", profile.winRate() * 100) + "% win rate§8)");
        sender.sendMessage("§7Tournaments: §f" + profile.tournamentWins() + " §7championships across §f"
            + profile.tournamentEntries() + " §7entries");
        sender.sendMessage("§7First seen: §f" + fmt.format(new Date(profile.firstSeenMillis()))
            + "  §7Last seen: §f" + fmt.format(new Date(profile.lastSeenMillis())));
        if (!profile.perKitStats().isEmpty()) {
            sender.sendMessage("§6--- per kit ---");
            for (KitStats k : profile.perKitStats().values()) {
                sender.sendMessage("§7" + k.kit() + ": §f" + k.wins() + "§7W §f" + k.losses() + "§7L  §8("
                    + String.format("%.0f", k.winRate() * 100) + "%)");
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return List.of();
    }
}
