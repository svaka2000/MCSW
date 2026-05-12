package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import com.samarth.duels.kit.Kit;
import com.samarth.duels.kit.KitRegistry;
import java.util.ArrayList;
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

public final class KitCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;
    private final KitRegistry kits;

    public KitCommand(DuelsPlugin plugin, KitRegistry kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("duels.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "save" -> handleSave(sender, args);
            case "list" -> handleList(sender);
            case "delete", "remove" -> handleDelete(sender, args);
            case "give", "equip" -> handleGive(sender, args);
            case "reload" -> {
                plugin.reloadConfig();
                kits.loadAll();
                sender.sendMessage("§aReloaded " + kits.names().size() + " kit(s).");
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only — your inventory becomes the kit.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /duelkit save <name>");
            return;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_-]+")) {
            sender.sendMessage("§cName must be alphanumeric (plus _ and -).");
            return;
        }
        Kit k = kits.saveFromPlayer(name, p);
        sender.sendMessage("§a[Duels] Saved kit §f" + k.name() + "§a from your current inventory.");
    }

    private void handleList(CommandSender sender) {
        List<String> names = kits.names();
        if (names.isEmpty()) {
            sender.sendMessage("§7No kits saved yet. Run §e/duelkit save <name>§7 while holding the kit.");
            return;
        }
        sender.sendMessage("§6=== Duel Kits ===");
        for (String n : names) sender.sendMessage("§7- §f" + n);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /duelkit delete <name>");
            return;
        }
        boolean removed = kits.delete(args[1]);
        sender.sendMessage(removed
            ? "§a[Duels] Deleted kit §f" + args[1] + "§a."
            : "§c[Duels] No kit named '" + args[1] + "'.");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /duelkit give <name> [player]");
            return;
        }
        Kit k = kits.get(args[1]);
        if (k == null) {
            sender.sendMessage("§cNo kit named '" + args[1] + "'.");
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§cPlayer not online.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cUsage from console: /duelkit give <name> <player>");
            return;
        }
        kits.equip(k, target);
        sender.sendMessage("§a[Duels] Equipped kit §f" + k.name() + "§a on §f" + target.getName() + "§a.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== /duelkit ===");
        sender.sendMessage("§e/duelkit save <name> §7— save your current inventory as a kit");
        sender.sendMessage("§e/duelkit list §7— show all saved kits");
        sender.sendMessage("§e/duelkit delete <name> §7— remove a kit");
        sender.sendMessage("§e/duelkit give <name> [player] §7— equip a kit");
        sender.sendMessage("§e/duelkit reload §7— reload kits from config");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("save", "list", "delete", "give", "reload"), args[0]);
        }
        if (args.length == 2 && Arrays.asList("delete", "give").contains(args[0].toLowerCase())) {
            return filter(kits.names(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : options) if (s.toLowerCase().startsWith(p)) out.add(s);
        return out;
    }
}
