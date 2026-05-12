package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Direct challenge: /duel &lt;player&gt; [kit] */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public DuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§7Usage: §e/duel <player> [kit]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        String kit;
        if (args.length >= 2) {
            kit = args[1];
        } else {
            kit = plugin.config().defaultKit();
            if (kit.isBlank()) {
                List<String> names = plugin.kits().names();
                if (names.isEmpty()) {
                    sender.sendMessage("§cNo kits saved yet. An op needs to run /duelkit save <name>.");
                    return true;
                }
                kit = names.get(0);
            }
        }
        // Direct challenge with current config defaults — the new GUI flow will land
        // in a follow-up commit (will open a kit picker and customize GUI here instead).
        plugin.challenges().challenge(p, target, kit, plugin.config().defaultBestOf(), false);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
            return names;
        }
        if (args.length == 2) {
            return plugin.kits().names();
        }
        return Collections.emptyList();
    }
}
