package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.listeners.NpcInteractionListener;
import com.samarth.duels.ui.QueueGui;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

public final class DuelsCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public DuelsCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "queue", "q" -> handleQueue(sender, args);
            case "leave" -> handleLeave(sender);
            case "accept" -> handleAccept(sender);
            case "deny", "decline" -> handleDeny(sender);
            case "gui" -> handleGui(sender);
            case "info", "status" -> handleInfo(sender);
            case "setlobby" -> handleSetLobby(sender);
            case "setarena" -> handleSetArena(sender, args);
            case "tagentity" -> handleTag(sender, true);
            case "untagentity" -> handleTag(sender, false);
            case "reload" -> handleReload(sender);
            case "help", "?" -> sendHelp(sender);
            default -> sender.sendMessage("§7Unknown subcommand. Try §e/duels help§7.");
        }
        return true;
    }

    private void handleQueue(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
        if (kits == null) {
            sender.sendMessage("§cPvPTLKits plugin not loaded — duels cannot run.");
            return;
        }
        String kit = args.length >= 2 ? args[1] : plugin.config().defaultKit();
        if (kit == null || kit.isBlank()) {
            List<String> names = kits.names();
            if (names.isEmpty()) {
                sender.sendMessage("§cNo kits saved. Ask an op to run /kitsave <name>.");
                return;
            }
            kit = names.get(0);
        }
        plugin.queues().enqueue(p, kit);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        plugin.queues().dequeue(p, true);
    }

    private void handleAccept(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        plugin.challenges().accept(p);
    }

    private void handleDeny(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        plugin.challenges().deny(p);
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
        if (kits == null) {
            sender.sendMessage("§cPvPTLKits plugin not loaded — duels cannot run.");
            return;
        }
        QueueGui.open(p, kits, plugin.queues());
    }

    private void handleInfo(CommandSender sender) {
        DuelsConfig c = plugin.config();
        com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
        int kitCount = kits == null ? 0 : kits.names().size();
        sender.sendMessage("§6=== Duels status ===");
        sender.sendMessage("§7Lobby: " + (c.lobby() == null ? "§c✗" : "§a✓ " + DuelsConfig.describe(c.lobby())));
        sender.sendMessage("§7Arena A: " + (c.arenaA() == null ? "§c✗" : "§a✓ " + DuelsConfig.describe(c.arenaA())));
        sender.sendMessage("§7Arena B: " + (c.arenaB() == null ? "§c✗" : "§a✓ " + DuelsConfig.describe(c.arenaB())));
        sender.sendMessage("§7PvPTLKits: " + (kits == null ? "§cnot loaded" : "§aloaded"));
        sender.sendMessage("§7Kits saved: §f" + kitCount);
        sender.sendMessage("§7Default first-to: §f" + c.defaultFirstTo());
        sender.sendMessage("§7Arena busy: " + (plugin.matches().isArenaBusy() ? "§ayes" : "§7no"));
    }

    private void handleSetLobby(CommandSender sender) {
        if (!sender.hasPermission("duels.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        plugin.config().setLobby(p.getLocation());
        sender.sendMessage("§aLobby saved at " + DuelsConfig.describe(p.getLocation()) + ".");
    }

    private void handleSetArena(CommandSender sender, String[] args) {
        if (!sender.hasPermission("duels.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        if (args.length < 2 || !(args[1].equalsIgnoreCase("a") || args[1].equalsIgnoreCase("b"))) {
            sender.sendMessage("§cUsage: /duels setarena <a|b>");
            return;
        }
        char side = args[1].toLowerCase().charAt(0);
        plugin.config().setArena(side, p.getLocation());
        sender.sendMessage("§aArena side " + side + " saved.");
    }

    private void handleTag(CommandSender sender, boolean tag) {
        if (!sender.hasPermission("duels.admin")) { sender.sendMessage("§cNo permission."); return; }
        if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return; }
        Entity target = lookAtEntity(p, 6.0);
        if (target == null) {
            sender.sendMessage("§cLook at an entity within 6 blocks and run the command again.");
            return;
        }
        if (tag) {
            target.addScoreboardTag(NpcInteractionListener.QUEUE_NPC_TAG);
            sender.sendMessage("§a[" + target.getType() + "] is now a duels queue NPC. Players who right-click it will see the queue GUI.");
        } else {
            target.removeScoreboardTag(NpcInteractionListener.QUEUE_NPC_TAG);
            sender.sendMessage("§7Removed queue NPC tag from " + target.getType() + ".");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("duels.admin")) { sender.sendMessage("§cNo permission."); return; }
        plugin.reloadConfig();
        plugin.config().reload();
        // Kits live in the PvPTLKits plugin now — tell the user how to reload them if needed.
        sender.sendMessage("§aDuels config reloaded.");
        sender.sendMessage("§7(Kits are managed by PvPTLKits — run §e/kitreload§7 to reload them.)");
    }

    private @org.jetbrains.annotations.Nullable Entity lookAtEntity(Player p, double maxDistance) {
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(),
            maxDistance, org.bukkit.FluidCollisionMode.NEVER, true, 0.3, e -> !e.equals(p));
        if (r == null) return null;
        return r.getHitEntity();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== /duels ===");
        sender.sendMessage("§e/duels queue [kit] §7— join the duel queue for a kit");
        sender.sendMessage("§e/duels leave §7— leave queue or current duel");
        sender.sendMessage("§e/duels accept §7— accept a pending /duel challenge");
        sender.sendMessage("§e/duels deny §7— deny a pending /duel challenge");
        sender.sendMessage("§e/duels gui §7— open the kit/queue picker");
        sender.sendMessage("§e/duels info §7— show plugin status");
        sender.sendMessage("§e/duel <player> [kit] §7— challenge someone directly");
        sender.sendMessage("§6Admin:");
        sender.sendMessage("§e/duels setlobby §7— save current location as the lobby");
        sender.sendMessage("§e/duels setarena <a|b> §7— save current location as arena spawn A or B");
        sender.sendMessage("§e/duels tagentity §7— mark the entity you're looking at as a queue NPC");
        sender.sendMessage("§e/duels untagentity §7— remove that tag");
        sender.sendMessage("§e/duels reload §7— reload duels config");
        sender.sendMessage("§e/kitsave <name> §7— save your inventory as a kit (PvPTLKits)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("queue", "leave", "accept", "deny", "gui", "info", "setlobby", "setarena", "tagentity", "untagentity", "reload", "help");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("queue")) {
            com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
            return kits == null ? Arrays.asList() : kits.names();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setarena")) {
            return Arrays.asList("a", "b");
        }
        return Collections.emptyList();
    }
}
