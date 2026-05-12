package com.samarth.stats;

import com.samarth.stats.commands.ProfileCommand;
import com.samarth.stats.commands.StatsCommand;
import com.samarth.stats.commands.TopCommand;
import com.samarth.stats.db.Database;
import com.samarth.stats.listeners.PresenceListener;
import java.io.File;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatsPlugin extends JavaPlugin {

    private Database db;
    private StatsServiceImpl service;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        File dbFile = new File(getDataFolder(), getConfig().getString("database.filename", "stats.db"));
        try {
            this.db = new Database(dbFile, getLogger());
        } catch (Exception e) {
            getLogger().severe("Failed to open stats database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.service = new StatsServiceImpl(this, db);

        // Expose via ServicesManager so Tourney/Duels can grab it via soft-depend.
        getServer().getServicesManager().register(
            StatsService.class, service, this, ServicePriority.Normal);

        bind("stats", new StatsCommand(this, service));
        bind("top", new TopCommand(this, service));
        bind("profile", new ProfileCommand(this, service));

        getServer().getPluginManager().registerEvents(new PresenceListener(service), this);

        getLogger().info("PvPTL Stats ready (SQLite at " + dbFile.getAbsolutePath() + ")");
    }

    @Override
    public void onDisable() {
        if (db != null) db.close();
    }

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
