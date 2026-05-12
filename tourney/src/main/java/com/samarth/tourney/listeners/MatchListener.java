package com.samarth.tourney.listeners;

import com.samarth.tourney.spectate.SpectatorService;
import com.samarth.tourney.tournament.Match;
import com.samarth.tourney.tournament.MatchState;
import com.samarth.tourney.tournament.TournamentManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MatchListener implements Listener {
    private final JavaPlugin plugin;
    private final TournamentManager manager;
    private final SpectatorService spec;

    public MatchListener(JavaPlugin plugin, TournamentManager manager, SpectatorService spec) {
        this.plugin = plugin;
        this.manager = manager;
        this.spec = spec;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getPlayer();
        Match m = manager.matchOf(victim.getUniqueId());
        if (m == null || m.state() != MatchState.FIGHTING) return;

        // No drops, no exp dropped, no death message
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(true); // belt and suspenders
        e.setKeepLevel(true);

        Player killer = victim.getKiller();
        manager.onPlayerKilled(victim, killer);

        // Auto-respawn after a brief delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isDead()) {
                victim.spigot().respawn();
            }
        }, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location spawn = manager.respawnLocationFor(p);
        if (spawn != null) {
            e.setRespawnLocation(spawn);
            manager.onPostRespawn(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match m = manager.matchOf(p.getUniqueId());
        if (m == null) return;
        if (m.state() == MatchState.PREP) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Match vm = manager.matchOf(victim.getUniqueId());
        if (vm == null) return;
        if (vm.state() == MatchState.PREP) {
            e.setCancelled(true);
            return;
        }
        // Block damage from non-match-participants (no spectator interference)
        if (e.getDamager() instanceof Player attacker) {
            Match am = manager.matchOf(attacker.getUniqueId());
            if (am == null || !am.id().equals(vm.id())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Match m = manager.matchOf(p.getUniqueId());
        if (m == null || m.state() != MatchState.PREP) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()
            || e.getFrom().getY() != e.getTo().getY()) {
            // Cancel non-rotation movement (allow head/yaw/pitch changes only)
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Match m = manager.matchOf(e.getPlayer().getUniqueId());
        if (m == null) return;
        e.setCancelled(true); // no kit theft
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Match m = manager.matchOf(p.getUniqueId());
        if (m == null) return;
        // Always keep food at 20 and saturation at 0 for match participants.
        e.setFoodLevel(20);
        p.setSaturation(0f);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        // Suppress vanilla advancement chat broadcasts while any tournament is active —
        // the diamond kit auto-grants "Cover Me With Diamonds" which spammed chat.
        if (manager.isActive()) {
            e.message(null);
        }
    }
}
