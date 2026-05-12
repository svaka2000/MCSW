package com.samarth.aihelp.commands;

import com.samarth.aihelp.AIHelpPlugin;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AIHelpCommand implements CommandExecutor, TabCompleter {

    private final AIHelpPlugin plugin;

    public AIHelpCommand(AIHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "setkey" -> handleSetKey(sender, args);
            case "status" -> handleStatus(sender);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleSetKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aihelp.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /aihelp setkey <groq-api-key>");
            return;
        }
        String key = args[1];
        plugin.getConfig().set("groq.api-key", key);
        plugin.saveConfig();
        plugin.rebuildGroqClient();
        sender.sendMessage("§aGroq key saved. (Length: " + key.length() + " chars)");
        sender.sendMessage("§7Run §e/ask hello §7to test the connection.");
    }

    private void handleStatus(CommandSender sender) {
        String key = plugin.getConfig().getString("groq.api-key", "");
        String model = plugin.getConfig().getString("groq.model", "?");
        sender.sendMessage("§6=== AI Help status ===");
        sender.sendMessage("§7Key configured: " + (key.isBlank() ? "§cNO" : "§aYES (" + key.length() + " chars)"));
        sender.sendMessage("§7Model: §f" + model);
        sender.sendMessage("§7Citizens loaded: " + (Bukkit.getPluginManager().getPlugin("Citizens") != null ? "§ayes" : "§7no"));
        sender.sendMessage("§7Chat watcher: " + (plugin.getConfig().getBoolean("watcher.enabled") ? "§aenabled" : "§7disabled"));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("aihelp.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 2) {
            if (sender instanceof Player p) {
                plugin.memory().clear(p.getUniqueId());
                sender.sendMessage("§aCleared your AI memory.");
            } else {
                sender.sendMessage("§cUsage from console: /aihelp reset <player>");
            }
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return;
        }
        plugin.memory().clear(target.getUniqueId());
        sender.sendMessage("§aCleared AI memory for §f" + target.getName() + "§a.");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("aihelp.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        plugin.reloadConfig();
        plugin.rebuildGroqClient();
        sender.sendMessage("§aReloaded AI help config.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== /aihelp ===");
        sender.sendMessage("§e/ask <question> §7— ask the AI a question");
        sender.sendMessage("§e/aihelp setkey <key> §7— set the Groq API key §c(op only)");
        sender.sendMessage("§e/aihelp status §7— show plugin status");
        sender.sendMessage("§e/aihelp reset [player] §7— clear AI memory");
        sender.sendMessage("§e/aihelp reload §7— reload config.yml");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("setkey", "status", "reset", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> names = new java.util.ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        return Collections.emptyList();
    }
}
