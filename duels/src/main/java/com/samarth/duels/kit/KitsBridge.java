package com.samarth.duels.kit;

import com.samarth.kits.KitService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Looks up the PvPTLKits {@link KitService} via Bukkit's ServicesManager.
 * Returns null when PvPTLKits isn't installed — callers must handle that case.
 */
public final class KitsBridge {
    private KitsBridge() {}

    public static @Nullable KitService tryGet() {
        if (Bukkit.getPluginManager().getPlugin("PvPTLKits") == null) return null;
        RegisteredServiceProvider<KitService> rsp =
            Bukkit.getServicesManager().getRegistration(KitService.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
