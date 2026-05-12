package com.samarth.kits;

import java.util.List;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Public API exposed via Bukkit's ServicesManager. Tourney and Duels look this up
 * via soft-dependency on PvPTLKits and use it for all kit storage / equipping.
 *
 * Soft-depend lookup pattern (see StatsBridge for an example):
 * <pre>
 *   var rsp = Bukkit.getServicesManager().getRegistration(KitService.class);
 *   KitService kits = rsp != null ? rsp.getProvider() : null;
 *   if (kits == null) return;  // PvPTLKits not installed — handle gracefully
 *   kits.equip("sword", player);
 * </pre>
 */
public interface KitService {

    /** Snapshot the player's current inventory + armor + offhand as a kit, persist, return it. */
    Kit saveFromPlayer(String name, Player p);

    /** Remove a saved kit. Returns true if anything was deleted. */
    boolean delete(String name);

    /** Look up a kit by name (case-insensitive). */
    @Nullable Kit get(String name);

    /** All saved kit names in alphabetical order. */
    List<String> names();

    /** Clear and re-equip the kit on the given player. Returns true if the kit exists. */
    boolean equip(String name, Player p);

    /** Force reload from disk (after manual config edits). */
    void reload();
}
