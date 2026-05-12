package com.samarth.kits.commands;

import com.samarth.kits.Kit;
import com.samarth.kits.KitService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * One class hosts all four kit commands. Each command implements its own
 * onCommand by switching on the alias label — keeps the wiring in
 * KitsPlugin simple (single executor object).
 */
public final class KitCommands implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final KitService kits;

    public KitCommands(JavaPlugin plugin, KitService kits) {
        this.plugin = plugin;
        this.kits = kits;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch (command.getName().toLowerCase()) {
            case "kitsave" -> handleSave(sender, args);
            case "kitlist" -> handleList(sender);
            case "kitdelete" -> handleDelete(sender, args);
            case "kitgive" -> handleGive(sender, args);
            case "kitreload" -> handleReload(sender);
        }
        return true;
    }

    private void handleSave(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pvptlkits.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only — your inventory becomes the kit.");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /kitsave <name>");
            return;
        }
        String name = args[0];
        if (!name.matches("[A-Za-z0-9_-]+")) {
            sender.sendMessage("§cName must be alphanumeric (plus _ and -).");
            return;
        }
        Kit k = kits.saveFromPlayer(name, p);
        p.sendMessage("§a[Kits] Saved kit §f" + k.name() + "§a from your current inventory.");
    }

    private void handleList(CommandSender sender) {
        List<String> names = kits.names();
        if (names.isEmpty()) {
            sender.sendMessage("§7No kits saved yet. Run §e/kitsave <name>§7 while holding the kit.");
            return;
        }
        sender.sendMessage("§6=== Saved kits (" + names.size() + ") ===");
        for (String n : names) sender.sendMessage("§7- §f" + n);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pvptlkits.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /kitdelete <name>");
            return;
        }
        boolean ok = kits.delete(args[0]);
        sender.sendMessage(ok ? "§a[Kits] Deleted '" + args[0] + "'."
                              : "§c[Kits] No kit named '" + args[0] + "'.");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /kitgive <name> [player]");
            return;
        }
        Kit k = kits.get(args[0]);
        if (k == null) {
            sender.sendMessage("§cNo kit named '" + args[0] + "'.");
            return;
        }
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("pvptlkits.admin")) {
                sender.sendMessage("§cNo permission to give kits to others.");
                return;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cPlayer not online.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cUsage from console: /kitgive <name> <player>");
            return;
        }
        kits.equip(k.name(), target);
        sender.sendMessage("§a[Kits] Equipped '" + k.name() + "' on §f" + target.getName() + "§a.");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("pvptlkits.admin")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        kits.reload();
        sender.sendMessage("§a[Kits] Reloaded " + kits.names().size() + " kit(s) from disk.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        String name = command.getName().toLowerCase();
        if (args.length == 1) {
            if (name.equals("kitdelete") || name.equals("kitgive")) {
                return filter(kits.names(), args[0]);
            }
            return Collections.emptyList();
        }
        if (args.length == 2 && name.equals("kitgive")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
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
