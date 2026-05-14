package com.samarth.duels;

import com.samarth.duels.challenge.ChallengeService;
import com.samarth.duels.commands.DuelCommand;
import com.samarth.duels.commands.DuelsCommand;
import com.samarth.duels.commands.LeaderboardCommand;
import com.samarth.duels.commands.LeaveCommand;
import com.samarth.duels.commands.PartyDuelCommand;
import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.listeners.LobbyListener;
import com.samarth.duels.listeners.MatchListener;
import com.samarth.duels.listeners.NpcInteractionListener;
import com.samarth.duels.lobby.LobbyItems;
import com.samarth.duels.match.MatchRunner;
import com.samarth.duels.queue.QueueService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuelsPlugin extends JavaPlugin {

    private DuelsConfig config;
    private MatchRunner matches;
    private QueueService queues;
    private ChallengeService challenges;
    private LobbyItems lobbyItems;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.config = new DuelsConfig(this);
        config.reload();

        this.matches = new MatchRunner(this, config);
        this.queues = new QueueService(this, config, matches);
        this.matches.setQueues(queues); // wire-back for post-match requeue item
        this.challenges = new ChallengeService(this, config, matches);
        this.lobbyItems = new LobbyItems(this);

        bind("duels", new DuelsCommand(this));
        bind("duel", new DuelCommand(this));
        bind("partyduel", new PartyDuelCommand(this));
        bind("elo", new LeaderboardCommand(this));
        bind("leave", new LeaveCommand(this));

        getServer().getPluginManager().registerEvents(new MatchListener(this, matches), this);
        getServer().getPluginManager().registerEvents(new NpcInteractionListener(queues, challenges, config, lobbyItems), this);
        getServer().getPluginManager().registerEvents(new LobbyListener(this, config, matches, lobbyItems), this);

        getLogger().info("Duels enabled. Run /duels info to check setup.");
        if (getServer().getPluginManager().getPlugin("PvPTLKits") == null) {
            getLogger().warning("PvPTLKits is NOT loaded — duels cannot run without it. Install PvPTLKits.");
        }
    }

    @Override
    public void onDisable() {
        // Match state is in-memory only — clean cancel happens via Bukkit shutting tasks down.
    }

    public DuelsConfig config() { return config; }
    public MatchRunner matches() { return matches; }
    public QueueService queues() { return queues; }
    public ChallengeService challenges() { return challenges; }
    public LobbyItems lobbyItems() { return lobbyItems; }

    private void bind(String name, Object executor) {
        PluginCommand pc = getCommand(name);
        if (pc == null) {
            getLogger().severe("Command '" + name + "' missing from plugin.yml");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) pc.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc) pc.setTabCompleter(tc);
    }
}
