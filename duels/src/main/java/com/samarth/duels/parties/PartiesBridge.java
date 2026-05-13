package com.samarth.duels.parties;

import com.samarth.parties.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Looks up the PvPTLParties {@link PartyService} via Bukkit's ServicesManager.
 * Returns null when PvPTLParties isn't installed — callers must handle that case
 * (the /partyduel command path is the only thing that needs it; 1v1 keeps working).
 */
public final class PartiesBridge {
    private PartiesBridge() {}

    public static @Nullable PartyService tryGet() {
        if (Bukkit.getPluginManager().getPlugin("PvPTLParties") == null) return null;
        RegisteredServiceProvider<PartyService> rsp =
            Bukkit.getServicesManager().getRegistration(PartyService.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
