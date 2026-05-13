package com.samarth.parties.commands;

import com.samarth.parties.PartyService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PartyChatCommand implements CommandExecutor {
    private final PartyService parties;

    public PartyChatCommand(PartyService parties) {
        this.parties = parties;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (args.length == 0) {
            p.sendMessage("§7Usage: §e/p <message>");
            return true;
        }
        parties.partyChat(p, String.join(" ", args));
        return true;
    }
}
