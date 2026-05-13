package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import com.samarth.duels.parties.PartiesBridge;
import com.samarth.duels.ui.DuelSetupHolder;
import com.samarth.parties.Party;
import com.samarth.parties.PartyService;
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

/**
 * /partyduel &lt;player&gt; — party-leader-only entry point to a team-vs-team duel.
 * Opens the same kit-picker GUI as /duel, but the resulting challenge is routed
 * to {@link com.samarth.duels.challenge.ChallengeService#partyChallenge}.
 */
public final class PartyDuelCommand implements CommandExecutor, TabCompleter {

    private final DuelsPlugin plugin;

    public PartyDuelCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§7Usage: §e/partyduel <player>");
            return true;
        }
        PartyService parties = PartiesBridge.tryGet();
        if (parties == null) {
            sender.sendMessage("§cPvPTLParties plugin not loaded — party duels unavailable.");
            return true;
        }
        Party myParty = parties.partyOf(p.getUniqueId());
        if (myParty == null) {
            sender.sendMessage("§cYou're not in a party. Run §e/party create §cfirst.");
            return true;
        }
        if (!myParty.isLeader(p.getUniqueId())) {
            sender.sendMessage("§cOnly the party leader can send a party challenge.");
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
        Party theirParty = parties.partyOf(target.getUniqueId());
        if (theirParty == null) {
            sender.sendMessage("§c" + target.getName() + " is not in a party.");
            return true;
        }
        if (!theirParty.isLeader(target.getUniqueId())) {
            sender.sendMessage("§c" + target.getName() + " is not the leader of their party. Challenge their leader instead.");
            return true;
        }
        if (myParty.id().equals(theirParty.id())) {
            sender.sendMessage("§cYou're both in the same party.");
            return true;
        }

        int maxSize = plugin.config().maxTeamSize();
        if (myParty.size() > maxSize || theirParty.size() > maxSize) {
            sender.sendMessage("§cArena only supports up to " + maxSize + "v" + maxSize
                + " — your party has " + myParty.size() + ", theirs has " + theirParty.size() + ".");
            return true;
        }

        com.samarth.kits.KitService kits = com.samarth.duels.kit.KitsBridge.tryGet();
        if (kits == null) {
            sender.sendMessage("§cPvPTLKits plugin not loaded — duels cannot run.");
            return true;
        }
        if (kits.names().isEmpty()) {
            sender.sendMessage("§cNo kits saved yet. An op needs to run /kitsave <name>.");
            return true;
        }
        DuelSetupHolder holder = new DuelSetupHolder(target.getUniqueId(), target.getName(), true);
        Inventory inv = holder.build(kits);
        p.openInventory(inv);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) names.add(pl.getName());
            return names;
        }
        return Collections.emptyList();
    }
}
