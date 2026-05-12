package com.samarth.stats.commands;

import com.samarth.stats.StatsService;
import com.samarth.stats.model.PlayerProfile;
import java.util.ArrayList;
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

/**
 * Lightweight profile view. Currently renders the same data as /stats with a
 * rank/cosmetic placeholder line. Real rank + cosmetic display will land when
 * the ranking system + Stripe integration are in.
 */
public final class ProfileCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final StatsService stats;

    public ProfileCommand(JavaPlugin plugin, StatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        UUID target;
        String fallback;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cUsage: /profile <player>");
                return true;
            }
            target = p.getUniqueId();
            fallback = p.getName();
        } else {
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
            target = op.getUniqueId();
            fallback = op.getName() == null ? args[0] : op.getName();
        }
        final String name = fallback;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile = stats.getProfile(target);
            Bukkit.getScheduler().runTask(plugin, () -> render(sender, profile, name));
        });
        return true;
    }

    private void render(CommandSender sender, PlayerProfile profile, String fallback) {
        sender.sendMessage("§6╔══════════════════════════════╗");
        if (profile == null) {
            sender.sendMessage("§6║  §fPlayer: §7" + fallback);
            sender.sendMessage("§6║  §7No stats yet");
        } else {
            sender.sendMessage("§6║  §fPlayer: §a" + profile.name());
            sender.sendMessage("§6║  §7Rank: §8[coming soon]");
            sender.sendMessage("§6║  §7Duels: §f" + profile.duelWins() + "§7W §f"
                + profile.duelLosses() + "§7L");
            sender.sendMessage("§6║  §7Championships: §6" + profile.tournamentWins());
            sender.sendMessage("§6║  §7Win rate: §e" + String.format("%.0f%%", profile.winRate() * 100));
        }
        sender.sendMessage("§6╚══════════════════════════════╝");
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
