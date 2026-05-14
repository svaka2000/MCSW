package com.samarth.duels.commands;

import com.samarth.duels.DuelsPlugin;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single-entry forfeit / leave command. Resolution order:
 *   1. In a tournament match  → forfeit (opponent wins)
 *   2. In tournament joining   → leave the joining roster
 *   3. In a duel match         → forfeit (opposing team wins)
 *   4. In a duel queue         → leave the queue
 *
 * Routes to Tourney via reflection-free plugin lookup so this command doesn't
 * hard-depend on the Tourney plugin being installed (PvPTLTourney is a soft-dep).
 */
public final class LeaveCommand implements CommandExecutor {

    private final DuelsPlugin plugin;

    public LeaveCommand(DuelsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        UUID id = p.getUniqueId();

        // 1 + 2: tournament path.
        com.samarth.tourney.tournament.TournamentManager tm = lookupTourneyManager();
        if (tm != null) {
            if (tm.matchOf(id) != null) {
                tm.forfeitMatch(p);
                p.sendMessage("§eYou forfeited your tournament match.");
                return true;
            }
            if (tm.isPlayerInJoining(id)) {
                tm.leaveTournament(p);
                // leaveTournament already sends "left" message
                return true;
            }
        }

        // 3: in a duel.
        if (plugin.matches().isInMatch(id)) {
            plugin.matches().forfeitMatch(p);
            p.sendMessage("§eYou forfeited your duel.");
            return true;
        }

        // 4: in a queue.
        if (plugin.queues().isQueued(id)) {
            plugin.queues().dequeue(p, true);
            return true;
        }

        p.sendMessage("§7You aren't in a duel, tournament, or queue.");
        return true;
    }

    private @Nullable com.samarth.tourney.tournament.TournamentManager lookupTourneyManager() {
        Plugin t = Bukkit.getPluginManager().getPlugin("Tourney");
        if (!(t instanceof com.samarth.tourney.TourneyPlugin tp)) return null;
        return tp.tournamentManager();
    }
}
