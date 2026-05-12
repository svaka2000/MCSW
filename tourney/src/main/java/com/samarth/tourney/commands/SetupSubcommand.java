package com.samarth.tourney.commands;

import com.samarth.tourney.config.TourneyConfig;
import com.samarth.tourney.tournament.Arena;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Interactive wizard for configuring arenas + lobby. Each subcommand operates on the sender's
 * current location. The plugin saves the result back to config.yml.
 *
 * Subcommands:
 *   /tourney setup            -> show status + next step
 *   /tourney setup lobby      -> save lobby at sender's location
 *   /tourney setup arena <name> <a|b>  -> save side a or b of the named arena
 *   /tourney setup arena remove <name> -> delete arena
 *   /tourney setup arena list -> list configured arenas
 *   /tourney setup done       -> validate and report
 */
public final class SetupSubcommand {
    private final TourneyConfig config;

    public SetupSubcommand(TourneyConfig config) {
        this.config = config;
    }

    public void handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tourney.setup")) {
            sender.sendMessage("§cNo permission.");
            return;
        }
        if (args.length == 0) {
            showStatus(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "lobby" -> handleLobby(sender);
            case "arena" -> handleArena(sender, Arrays.copyOfRange(args, 1, args.length));
            case "done" -> showStatus(sender);
            default -> {
                sender.sendMessage("§cUnknown setup subcommand. Try §e/tourney setup§c.");
            }
        }
    }

    private void handleLobby(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cRun this in-game from the lobby spawn point.");
            return;
        }
        config.setLobby(p.getLocation());
        p.sendMessage("§a[Tourney] Lobby spawn saved at §f" + TourneyConfig.describeLocation(p.getLocation()) + "§a.");
        showStatus(p);
    }

    private void handleArena(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/tourney setup arena <name> <a|b>");
            sender.sendMessage("§e/tourney setup arena remove <name>");
            sender.sendMessage("§e/tourney setup arena list");
            return;
        }
        String first = args[0].toLowerCase();

        if ("list".equals(first)) {
            handleList(sender);
            return;
        }
        if ("remove".equals(first)) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /tourney setup arena remove <name>");
                return;
            }
            String name = args[1];
            if (!config.arenaCompletionStatus().containsKey(name)) {
                sender.sendMessage("§cNo such arena: " + name);
                return;
            }
            config.removeArena(name);
            sender.sendMessage("§7[Tourney] Removed arena '" + name + "'.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /tourney setup arena <name> <a|b>");
            return;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cRun this in-game while standing at the arena spawn point.");
            return;
        }
        String name = args[0];
        String side = args[1].toLowerCase();
        if (!"a".equals(side) && !"b".equals(side)) {
            sender.sendMessage("§cSide must be 'a' or 'b'.");
            return;
        }
        if (!isValidName(name)) {
            sender.sendMessage("§cArena names must be alphanumeric (and dashes/underscores only).");
            return;
        }
        config.setArenaSpawn(name, side.charAt(0), p.getLocation());
        p.sendMessage("§a[Tourney] Saved arena '" + name + "' side " + side + " at §f"
            + TourneyConfig.describeLocation(p.getLocation()) + "§a.");
        showStatus(p);
    }

    private void handleList(CommandSender sender) {
        Map<String, Map<String, Boolean>> status = config.arenaCompletionStatus();
        if (status.isEmpty()) {
            sender.sendMessage("§7No arenas configured. Use §e/tourney setup arena <name> a§7 and §e/tourney setup arena <name> b§7.");
            return;
        }
        sender.sendMessage("§6=== Configured arenas ===");
        for (Map.Entry<String, Map<String, Boolean>> e : status.entrySet()) {
            boolean hasA = Boolean.TRUE.equals(e.getValue().get("a"));
            boolean hasB = Boolean.TRUE.equals(e.getValue().get("b"));
            String aMark = hasA ? "§a✓" : "§c✗";
            String bMark = hasB ? "§a✓" : "§c✗";
            sender.sendMessage("§7- §f" + e.getKey() + " §7[a " + aMark + "§7  b " + bMark + "§7]");
        }
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage("§6=== Tourney setup status ===");
        sender.sendMessage("§7Lobby: " + (config.lobby() == null ? "§c✗ unset" : "§a✓ " + TourneyConfig.describeLocation(config.lobby())));
        Map<String, Map<String, Boolean>> status = config.arenaCompletionStatus();
        if (status.isEmpty()) {
            sender.sendMessage("§7Arenas: §cnone configured");
        } else {
            sender.sendMessage("§7Arenas: §f" + status.size());
            int complete = 0;
            for (Map.Entry<String, Map<String, Boolean>> e : status.entrySet()) {
                boolean ok = Boolean.TRUE.equals(e.getValue().get("a")) && Boolean.TRUE.equals(e.getValue().get("b"));
                if (ok) complete++;
                String state = ok ? "§a✓ ready" : "§e... incomplete";
                sender.sendMessage("§7  - §f" + e.getKey() + " " + state);
            }
            sender.sendMessage("§7Ready arenas (parallel matches possible): §f" + complete);
        }
        sender.sendMessage("§8─────────────");
        if (config.lobby() == null) {
            sender.sendMessage("§e→ Stand at the lobby spawn and run §f/tourney setup lobby");
        }
        Map<String, Arena> ready = config.arenas();
        if (ready.isEmpty()) {
            sender.sendMessage("§e→ Stand at an arena spawn point and run §f/tourney setup arena <name> a");
            sender.sendMessage("§e→ Then move to the second spawn and run §f/tourney setup arena <name> b");
            sender.sendMessage("§e→ Repeat with new names for parallel matches (e.g. arena1, arena2, arena3).");
        }
        if (config.lobby() != null && !ready.isEmpty()) {
            sender.sendMessage("§a✓ Setup complete. Run §f/tourney start §a to begin.");
        }
    }

    private boolean isValidName(String name) {
        return name.matches("[A-Za-z0-9_-]+");
    }

    public List<String> tabComplete(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("lobby", "arena", "done");
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("arena")) {
            if (args.length == 2) {
                List<String> opts = new ArrayList<>();
                opts.add("list");
                opts.add("remove");
                opts.addAll(config.arenaCompletionStatus().keySet());
                return opts;
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("remove")) {
                    return new ArrayList<>(config.arenaCompletionStatus().keySet());
                }
                if (args[1].equalsIgnoreCase("list")) {
                    return Collections.emptyList();
                }
                return Arrays.asList("a", "b");
            }
        }
        return Collections.emptyList();
    }
}
