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
            case "queue", "q", "unranked" -> handleQueue(sender, args, false);
            case "ranked", "r" -> handleQueue(sender, args, true);
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

    private void handleQueue(CommandSender sender, String[] args, boolean ranked) {
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
        plugin.queues().enqueue(p, kit, ranked);
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
        sender.sendMessage("§7Arenas configured: §f" + c.totalArenas());
        for (com.samarth.duels.config.Arena a : c.arenas()) {
            sender.sendMessage("  §7- §f" + a.id()
                + " §8(§7a=" + a.spawnsA().size() + ", b=" + a.spawnsB().size()
                + ", max team " + a.maxTeamSize() + "§8)");
        }
        sender.sendMessage("§7Max team size (best arena): §f" + c.maxTeamSize());
        sender.sendMessage("§7PvPTLKits: " + (kits == null ? "§cnot loaded" : "§aloaded"));
        sender.sendMessage("§7Kits saved: §f" + kitCount);
        sender.sendMessage("§7Default first-to: §f" + c.defaultFirstTo());
        sender.sendMessage("§7Active matches: §f" + plugin.matches().activeMatchCount()
            + " §7| Waiting: §f" + plugin.matches().waitingMatchCount());
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
        if (args.length < 2) {
            sender.sendMessage("§7Usage:");
            sender.sendMessage("§e/duels setarena <a|b> §7— append spawn at your location (default arena)");
            sender.sendMessage("§e/duels setarena <arena> <a|b> §7— append spawn in a specific arena");
            sender.sendMessage("§e/duels setarena <arena> <a|b> <slot> §7— overwrite a slot");
            sender.sendMessage("§e/duels setarena list §7— list all arenas + spawns");
            sender.sendMessage("§e/duels setarena clear <arena> [a|b] §7— wipe an arena (or one side)");
            sender.sendMessage("§e/duels setarena delete <arena> §7— remove arena entirely");
            return;
        }
        String first = args[1].toLowerCase();
        if (first.equals("list")) {
            handleArenaList(sender);
            return;
        }
        if (first.equals("delete")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /duels setarena delete <arena>");
                return;
            }
            plugin.config().deleteArena(args[2]);
            sender.sendMessage("§aArena §f" + args[2] + " §adeleted.");
            return;
        }
        if (first.equals("clear")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /duels setarena clear <arena> [a|b]");
                return;
            }
            String arenaId = args[2];
            char side = 'x';
            if (args.length >= 4) {
                String s = args[3].toLowerCase();
                if (!(s.equals("a") || s.equals("b"))) {
                    sender.sendMessage("§cSide must be 'a' or 'b'.");
                    return;
                }
                side = s.charAt(0);
            }
            plugin.config().clearSpawns(arenaId, side);
            sender.sendMessage("§aArena §f" + arenaId + " §a"
                + (side == 'x' ? "fully cleared." : "side " + side + " cleared."));
            return;
        }

        // Forms:
        //   /duels setarena <a|b>                          → default arena, append
        //   /duels setarena <a|b> <slot>                   → default arena, overwrite slot
        //   /duels setarena <arena> <a|b>                  → named arena, append
        //   /duels setarena <arena> <a|b> <slot>           → named arena, overwrite slot
        String arenaId;
        String sideStr;
        int slotArgIdx;
        if (first.equals("a") || first.equals("b")) {
            arenaId = DuelsConfig.DEFAULT_ARENA_ID;
            sideStr = first;
            slotArgIdx = 2;
        } else {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /duels setarena <arena> <a|b> [slot]");
                return;
            }
            String maybeSide = args[2].toLowerCase();
            if (!(maybeSide.equals("a") || maybeSide.equals("b"))) {
                sender.sendMessage("§cSecond arg must be 'a' or 'b'.");
                return;
            }
            arenaId = args[1];
            sideStr = maybeSide;
            slotArgIdx = 3;
        }
        char side = sideStr.charAt(0);
        int slot = -1;
        if (args.length > slotArgIdx) {
            try {
                slot = Integer.parseInt(args[slotArgIdx]);
                if (slot < 0) { sender.sendMessage("§cSlot must be ≥ 0."); return; }
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cSlot must be a number.");
                return;
            }
        }
        int written = plugin.config().setSpawn(arenaId, side, slot, p.getLocation());
        sender.sendMessage("§aArena §f" + arenaId + " §aside §f" + side
            + " §aslot §f" + written + " §asaved at " + DuelsConfig.describe(p.getLocation()) + ".");
    }

    private void handleArenaList(CommandSender sender) {
        DuelsConfig c = plugin.config();
        sender.sendMessage("§6=== Arenas (" + c.totalArenas() + ") ===");
        for (com.samarth.duels.config.Arena a : c.arenas()) {
            sender.sendMessage("§e" + a.id() + " §8| §7max team " + a.maxTeamSize());
            var listA = a.spawnsA();
            var listB = a.spawnsB();
            for (int i = 0; i < listA.size(); i++) {
                sender.sendMessage("  §bA[" + i + "] §f" + DuelsConfig.describe(listA.get(i)));
            }
            for (int i = 0; i < listB.size(); i++) {
                sender.sendMessage("  §cB[" + i + "] §f" + DuelsConfig.describe(listB.get(i)));
            }
        }
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
        sender.sendMessage("§e/duels ranked [kit] §7— join the §6RANKED§7 queue (first-to-3, Elo)");
        sender.sendMessage("§e/duels unranked [kit] §7— join the casual queue");
        sender.sendMessage("§7(or right-click the §bdiamond§7/§firon§7 sword in your hotbar)");
        sender.sendMessage("§e/duels queue [kit] §7— alias for unranked");
        sender.sendMessage("§e/elo top <kit> §7— ranked Elo leaderboard");
        sender.sendMessage("§e/leave §7— forfeit your duel/tournament or leave queue");
        sender.sendMessage("§e/duels accept §7— accept a pending /duel challenge");
        sender.sendMessage("§e/duels deny §7— deny a pending /duel challenge");
        sender.sendMessage("§e/duels gui §7— open the kit/queue picker");
        sender.sendMessage("§e/duels info §7— show plugin status");
        sender.sendMessage("§e/duel <player> [kit] §7— challenge someone directly");
        sender.sendMessage("§e/partyduel <player> §7— challenge another party leader (team-vs-team)");
        sender.sendMessage("§e/party §7— manage parties (PvPTLParties)");
        sender.sendMessage("§6Admin:");
        sender.sendMessage("§e/duels setlobby §7— save current location as the lobby");
        sender.sendMessage("§e/duels setarena <a|b> [slot] §7— add or replace an arena spawn (omit slot to append)");
        sender.sendMessage("§e/duels setarena clear <a|b> §7— wipe a side; §e/duels setarena list §7— list spawns");
        sender.sendMessage("§e/duels tagentity §7— mark the entity you're looking at as a queue NPC");
        sender.sendMessage("§e/duels untagentity §7— remove that tag");
        sender.sendMessage("§e/duels reload §7— reload duels config");
        sender.sendMessage("§e/kitsave <name> §7— save your inventory as a kit (PvPTLKits)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("ranked", "unranked", "queue", "leave", "accept", "deny", "gui", "info", "setlobby", "setarena", "tagentity", "untagentity", "reload", "help");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("queue")
                || args[0].equalsIgnoreCase("ranked") || args[0].equalsIgnoreCase("unranked"))) {
            com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
            return kits == null ? Arrays.asList() : kits.names();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setarena")) {
            List<String> opts = new ArrayList<>(Arrays.asList("a", "b", "clear", "list", "delete"));
            for (com.samarth.duels.config.Arena a : plugin.config().arenas()) opts.add(a.id());
            return opts;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setarena")) {
            String sub = args[1].toLowerCase();
            if (sub.equals("clear") || sub.equals("delete")) {
                List<String> ids = new ArrayList<>();
                for (com.samarth.duels.config.Arena a : plugin.config().arenas()) ids.add(a.id());
                return ids;
            }
            // Probably an arena name → expect a|b next.
            return Arrays.asList("a", "b");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setarena")
            && args[1].equalsIgnoreCase("clear")) {
            return Arrays.asList("a", "b");
        }
        return Collections.emptyList();
    }
}
