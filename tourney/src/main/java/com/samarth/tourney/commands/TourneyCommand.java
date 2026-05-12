package com.samarth.tourney.commands;

import com.samarth.tourney.config.TourneyConfig;
import com.samarth.tourney.spectate.SpectatorService;
import com.samarth.tourney.tournament.Match;
import com.samarth.tourney.tournament.Tournament;
import com.samarth.tourney.tournament.TournamentManager;
import com.samarth.tourney.ui.BracketGui;
import com.samarth.tourney.ui.SpectateGui;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class TourneyCommand implements CommandExecutor, TabCompleter {
    private final TournamentManager manager;
    private final TourneyConfig config;
    private final SpectatorService spec;
    private final SetupSubcommand setup;

    public TourneyCommand(TournamentManager manager, TourneyConfig config, SpectatorService spec, SetupSubcommand setup) {
        this.manager = manager;
        this.config = config;
        this.spec = spec;
        this.setup = setup;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "start" -> handleStart(sender, rest);
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "cancel", "stop", "end" -> handleCancel(sender);
            case "bracket" -> handleBracket(sender);
            case "spectate", "spec" -> handleSpectate(sender, rest);
            case "stopspectate", "unspec", "unspectate" -> handleStopSpectate(sender);
            case "setup" -> setup.handle(sender, rest);
            case "reload" -> handleReload(sender);
            case "help", "?" -> sendHelp(sender);
            default -> {
                sender.sendMessage("§7[Tourney] Unknown subcommand. Try §e/tourney help§7.");
            }
        }
        return true;
    }

    private void handleStart(CommandSender sender, String[] rest) {
        if (!sender.hasPermission("tourney.start")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        Map<String, Integer> overrides = parseOverrides(sender, rest);
        manager.startTournament(sender, overrides);
    }

    /** Parse key=value args ("join=120 rounds=3 freeze=5 cap=300"). Warns on unknown keys. */
    private Map<String, Integer> parseOverrides(CommandSender sender, String[] rest) {
        Map<String, Integer> out = new HashMap<>();
        List<String> known = Arrays.asList("join", "rounds", "freeze", "cap");
        for (String arg : rest) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                sender.sendMessage("§eIgnored arg '" + arg + "' — use key=value form (e.g. rounds=3).");
                continue;
            }
            String key = arg.substring(0, eq).toLowerCase();
            String val = arg.substring(eq + 1);
            if (!known.contains(key)) {
                sender.sendMessage("§eUnknown key '" + key + "'. Allowed: join, rounds, freeze, cap.");
                continue;
            }
            try {
                int n = Integer.parseInt(val);
                if (n < 0) {
                    sender.sendMessage("§eValue for '" + key + "' must be non-negative.");
                    continue;
                }
                out.put(key, n);
            } catch (NumberFormatException e) {
                sender.sendMessage("§eValue for '" + key + "' must be a number.");
            }
        }
        return out;
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }
        manager.joinTournament(p);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }
        manager.leaveTournament(p);
    }

    private void handleCancel(CommandSender sender) {
        if (!sender.hasPermission("tourney.cancel")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        manager.cancelTournament(sender);
    }

    private void handleBracket(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }
        Tournament t = manager.active();
        if (t == null || t.bracket() == null) {
            sender.sendMessage("§7[Tourney] No active bracket.");
            return;
        }
        BracketGui.open(p, t);
    }

    private void handleSpectate(CommandSender sender, String[] rest) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }
        Tournament t = manager.active();
        if (t == null) {
            sender.sendMessage("§7[Tourney] No active tournament.");
            return;
        }
        if (rest.length == 0) {
            SpectateGui.open(viewer, t);
            return;
        }
        Player target = Bukkit.getPlayer(rest[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not online.");
            return;
        }
        Match m = manager.findMatchByPlayer(target.getUniqueId());
        if (m == null) {
            sender.sendMessage("§cThat player is not in an active match.");
            return;
        }
        manager.startSpectating(viewer, target);
        viewer.sendMessage("§7You are now spectating §b" + target.getName() + "§7.");
    }

    private void handleStopSpectate(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        if (!spec.isSpectator(p.getUniqueId())) {
            sender.sendMessage("§7You are not spectating anyone.");
            return;
        }
        manager.stopSpectating(p);
        sender.sendMessage("§7Stopped spectating.");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("tourney.setup")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        config.reload();
        sender.sendMessage("§7[Tourney] Config reloaded.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== Tourney commands ===");
        sender.sendMessage("§e/tourney start [join=N] [rounds=N] [freeze=N] [cap=N]");
        sender.sendMessage("§7  begin a tournament; all params optional, default 5-min join + first-to-5");
        sender.sendMessage("§e/tourney join §7— join the active tournament");
        sender.sendMessage("§e/tourney leave §7— leave before it starts");
        sender.sendMessage("§e/tourney cancel §7(aliases: stop, end) §7— cancel the tournament (admin)");
        sender.sendMessage("§e/tourney bracket §7— view the bracket");
        sender.sendMessage("§e/tourney spectate [player] §7— spectate a match (or open list)");
        sender.sendMessage("§e/tourney stopspectate §7— exit spectator mode");
        sender.sendMessage("§e/tourney setup §7— wizard for arenas + lobby");
        sender.sendMessage("§e/tourney reload §7— reload config");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Arrays.asList(
                "start", "join", "leave", "cancel", "stop", "bracket",
                "spectate", "stopspectate", "setup", "reload", "help"));
            return filter(opts, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("setup")) {
            return setup.tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("spectate") || args[0].equalsIgnoreCase("spec"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("start")) {
            String last = args[args.length - 1];
            if (!last.contains("=")) {
                return filter(Arrays.asList("join=", "rounds=", "freeze=", "cap="), last);
            }
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase().startsWith(p)) out.add(s);
        }
        return out;
    }
}
