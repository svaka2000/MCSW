package com.samarth.tourney;

import com.samarth.tourney.commands.SetupSubcommand;
import com.samarth.tourney.commands.TourneyCommand;
import com.samarth.tourney.config.TourneyConfig;
import com.samarth.tourney.listeners.GuiListener;
import com.samarth.tourney.listeners.MatchListener;
import com.samarth.tourney.listeners.PresenceListener;
import com.samarth.tourney.persistence.StateStore;
import com.samarth.tourney.spectate.SpectatorService;
import com.samarth.tourney.tournament.TournamentManager;
import com.samarth.tourney.ui.Hud;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TourneyPlugin extends JavaPlugin {

    private TourneyConfig config;
    private SpectatorService spec;
    private Hud hud;
    private StateStore store;
    private TournamentManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = new TourneyConfig(this);
        config.reload();

        this.spec = new SpectatorService(config);
        this.hud = new Hud(config);
        this.store = new StateStore(this);
        this.manager = new TournamentManager(this, config, spec, hud, store);

        SetupSubcommand setup = new SetupSubcommand(config);
        TourneyCommand cmd = new TourneyCommand(manager, config, spec, setup);

        PluginCommand pc = getCommand("tourney");
        if (pc == null) {
            getLogger().severe("Command 'tourney' is missing from plugin.yml — disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new MatchListener(this, manager, spec), this);
        getServer().getPluginManager().registerEvents(new PresenceListener(manager, store), this);
        getServer().getPluginManager().registerEvents(new GuiListener(manager, spec), this);

        getLogger().info("Tourney enabled. Run /tourney setup to configure.");
    }

    @Override
    public void onDisable() {
        // Best-effort: cancel any in-flight tournament so player inventories are restored.
        if (manager != null && manager.isActive()) {
            getLogger().info("Cancelling active tournament for shutdown — restoring inventories.");
            manager.cancelTournament(getServer().getConsoleSender());
        }
    }
}
