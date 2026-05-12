package com.samarth.duels.stats;

import com.samarth.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper that looks up the PvPTLStats StatsService via Bukkit's
 * ServicesManager. Returns null if PvPTLStats isn't loaded — callers must
 * null-check and skip recording when absent.
 */
public final class StatsBridge {
    private StatsBridge() {}

    public static @Nullable StatsService tryGet() {
        if (Bukkit.getPluginManager().getPlugin("PvPTLStats") == null) return null;
        RegisteredServiceProvider<StatsService> rsp =
            Bukkit.getServicesManager().getRegistration(StatsService.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
