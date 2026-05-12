package com.samarth.duels;

import com.samarth.duels.challenge.ChallengeService;
import com.samarth.duels.commands.DuelCommand;
import com.samarth.duels.commands.DuelsCommand;
import com.samarth.duels.commands.KitCommand;
import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitRegistry;
import com.samarth.duels.listeners.MatchListener;
import com.samarth.duels.listeners.NpcInteractionListener;
import com.samarth.duels.match.MatchRunner;
import com.samarth.duels.queue.QueueService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DuelsPlugin extends JavaPlugin {

    private DuelsConfig config;
    private KitRegistry kits;
    private MatchRunner matches;
    private QueueService queues;
    private ChallengeService challenges;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.config = new DuelsConfig(this);
        config.reload();
        this.kits = new KitRegistry(this);
        kits.loadAll();

        this.matches = new MatchRunner(this, config, kits);
        this.queues = new QueueService(config, kits, matches);
        this.challenges = new ChallengeService(this, config, kits, matches);

        bind("duels", new DuelsCommand(this));
        bind("duel", new DuelCommand(this));
        bind("duelkit", new KitCommand(this, kits));

        getServer().getPluginManager().registerEvents(new MatchListener(this, matches), this);
        getServer().getPluginManager().registerEvents(new NpcInteractionListener(kits, queues, challenges, config), this);

        getLogger().info("Duels enabled. Run /duels info to check setup.");
    }

    @Override
    public void onDisable() {
        // Match state is in-memory only — clean cancel happens via Bukkit shutting tasks down.
    }

    public DuelsConfig config() { return config; }
    public KitRegistry kits() { return kits; }
    public MatchRunner matches() { return matches; }
    public QueueService queues() { return queues; }
    public ChallengeService challenges() { return challenges; }

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
