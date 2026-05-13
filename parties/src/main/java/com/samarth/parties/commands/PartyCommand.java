package com.samarth.parties.commands;

import com.samarth.parties.Party;
import com.samarth.parties.PartyService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final PartyService parties;

    public PartyCommand(JavaPlugin plugin, PartyService parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create", "new" -> parties.create(p);
            case "invite", "add" -> handleInvite(p, args);
            case "accept", "yes" -> parties.accept(p);
            case "deny", "decline", "no" -> parties.deny(p);
            case "leave", "quit" -> parties.leave(p);
            case "disband" -> parties.disband(p);
            case "kick", "remove" -> handleKick(p, args);
            case "promote", "transfer" -> handlePromote(p, args);
            case "info", "list", "members" -> handleInfo(p);
            case "chat" -> {
                if (args.length < 2) { p.sendMessage("§7Usage: §e/party chat <message>"); return true; }
                String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                parties.partyChat(p, msg);
            }
            case "help", "?" -> sendHelp(sender);
            default -> p.sendMessage("§7[Party] Unknown subcommand. Try §e/party help§7.");
        }
        return true;
    }

    private void handleInvite(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§7Usage: §e/party invite <player>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { p.sendMessage("§cPlayer not online."); return; }
        parties.invite(p, target);
    }

    private void handleKick(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§7Usage: §e/party kick <player>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            // Allow kicking offline members by name lookup against the party
            Party party = parties.partyOf(p.getUniqueId());
            if (party == null) { p.sendMessage("§cYou're not in a party."); return; }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
            if (!party.contains(offline.getUniqueId())) { p.sendMessage("§cNot a party member."); return; }
            // Create a synthetic Player wrapper isn't easy — just call kick with online target only.
            p.sendMessage("§cThat player isn't online. Use /party kick for online members.");
            return;
        }
        parties.kick(p, target);
    }

    private void handlePromote(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage("§7Usage: §e/party promote <player>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { p.sendMessage("§cPlayer not online."); return; }
        parties.promote(p, target);
    }

    private void handleInfo(Player p) {
        Party party = parties.partyOf(p.getUniqueId());
        if (party == null) { p.sendMessage("§7You're not in a party."); return; }
        p.sendMessage("§6=== Your party (" + party.size() + " members) ===");
        for (java.util.UUID id : party.members()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            String name = op.getName() == null ? id.toString().substring(0, 8) : op.getName();
            String marker = party.isLeader(id) ? "§6★ " : "§7  ";
            String onlineColor = op.isOnline() ? "§a" : "§8";
            p.sendMessage(marker + onlineColor + name);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== /party ===");
        sender.sendMessage("§e/party create §7— create a new party with you as leader");
        sender.sendMessage("§e/party invite <player> §7— invite someone (auto-creates a party if needed)");
        sender.sendMessage("§e/party accept §7— accept the pending invite");
        sender.sendMessage("§e/party deny §7— deny it");
        sender.sendMessage("§e/party leave §7— leave your current party");
        sender.sendMessage("§e/party disband §7— leader only, remove everyone");
        sender.sendMessage("§e/party kick <player> §7— leader only");
        sender.sendMessage("§e/party promote <player> §7— transfer leadership");
        sender.sendMessage("§e/party info §7— show your party's roster");
        sender.sendMessage("§e/p <message> §7— party chat (or /party chat <msg>)");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("create", "invite", "accept", "deny", "leave", "disband",
                "kick", "promote", "info", "chat", "help"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("promote")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[1]);
            }
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> opts, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : opts) if (s.toLowerCase().startsWith(p)) out.add(s);
        return out;
    }
}
