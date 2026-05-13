package com.samarth.parties;

import com.samarth.parties.commands.PartyChatCommand;
import com.samarth.parties.commands.PartyCommand;
import com.samarth.parties.listeners.PartyPresenceListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PartiesPlugin extends JavaPlugin {

    private PartyManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.manager = new PartyManager(this);

        getServer().getServicesManager().register(
            PartyService.class, manager, this, ServicePriority.Normal);

        PartyCommand partyCmd = new PartyCommand(this, manager);
        PartyChatCommand partyChatCmd = new PartyChatCommand(manager);
        bind("party", partyCmd, partyCmd);
        bind("p", partyChatCmd, null);

        getServer().getPluginManager().registerEvents(new PartyPresenceListener(manager), this);
        getLogger().info("PvPTL Parties ready.");
    }

    private void bind(String name, org.bukkit.command.CommandExecutor exec,
                       org.bukkit.command.TabCompleter tab) {
        PluginCommand pc = getCommand(name);
        if (pc == null) {
            getLogger().severe("Command '" + name + "' missing from plugin.yml");
            return;
        }
        pc.setExecutor(exec);
        if (tab != null) pc.setTabCompleter(tab);
    }
}
