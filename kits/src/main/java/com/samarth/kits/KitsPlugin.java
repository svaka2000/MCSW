package com.samarth.kits;

import com.samarth.kits.commands.KitCommands;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class KitsPlugin extends JavaPlugin {

    private KitRegistry registry;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.registry = new KitRegistry(this);
        registry.reload();

        // Expose via ServicesManager so Tourney and Duels can soft-depend.
        getServer().getServicesManager().register(
            KitService.class, registry, this, ServicePriority.Normal);

        KitCommands cmds = new KitCommands(this, registry);
        bind("kitsave", cmds);
        bind("kitlist", cmds);
        bind("kitdelete", cmds);
        bind("kitgive", cmds);
        bind("kitreload", cmds);

        getLogger().info("PvPTL Kits ready — " + registry.names().size() + " kit(s) loaded.");
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
