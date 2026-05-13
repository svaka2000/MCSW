package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import com.samarth.duels.ui.DuelSetupHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/** /duel &lt;player&gt; — opens the kit-picker GUI. Kit name and customization happen in the GUI. */
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
            sender.sendMessage("§7Usage: §e/duel <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return true;
        }
        if (target.equals(p)) {
            sender.sendMessage("§cYou can't duel yourself.");
            return true;
        }
        if (plugin.matches().isInMatch(p.getUniqueId()) || plugin.matches().isInMatch(target.getUniqueId())) {
            sender.sendMessage("§cOne of you is already in a duel.");
            return true;
        }
        com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
        if (kits == null) {
            sender.sendMessage("§cPvPTLKits plugin not loaded — duels cannot run.");
            return true;
        }
        List<String> kitNames = kits.names();
        if (kitNames.isEmpty()) {
            sender.sendMessage("§cNo kits saved yet. An op needs to run /kitsave <name>.");
            return true;
        }
        DuelSetupHolder holder = new DuelSetupHolder(target.getUniqueId(), target.getName());
        Inventory inv = holder.build(kits);
        p.openInventory(inv);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
            return names;
        }
        return Collections.emptyList();
    }
}
