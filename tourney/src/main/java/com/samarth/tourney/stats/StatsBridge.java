package com.samarth.tourney.stats;

import com.samarth.stats.StatsService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper that looks up the PvPTLStats StatsService via Bukkit's
 * ServicesManager. Returns null if PvPTLStats isn't loaded — callers must
 * null-check and skip recording when absent.
 *
 * The Tourney plugin soft-depends on PvPTLStats (plugin.yml). At compile time,
 * we depend on the :stats subproject compileOnly so this class file resolves;
 * at runtime, Bukkit's plugin classloader will or won't find PvPTLStats.
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
