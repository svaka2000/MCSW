package com.samarth.duels.listeners;

import com.samarth.duels.match.DuelMatch;
import com.samarth.duels.match.MatchRunner;
import com.samarth.duels.match.MatchState;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MatchListener implements Listener {
    private final JavaPlugin plugin;
    private final MatchRunner runner;

    public MatchListener(JavaPlugin plugin, MatchRunner runner) {
        this.plugin = plugin;
        this.runner = runner;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getPlayer();
        DuelMatch m = runner.matchOf(victim.getUniqueId());
        if (m == null || m.state() != MatchState.FIGHTING) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        runner.onPlayerKilled(victim, victim.getKiller());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (victim.isDead()) victim.spigot().respawn();
        }, 5L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location spawn = runner.respawnLocationFor(p);
        if (spawn != null) {
            e.setRespawnLocation(spawn);
            runner.onPostRespawn(p);
            return;
        }
        // If a match just ended while this player was dead, send them to the lobby
        // (or their pre-duel location) instead of the world default spawn.
        Location post = runner.consumePostMatchTeleport(p.getUniqueId());
        if (post != null) {
            e.setRespawnLocation(post);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        DuelMatch m = runner.matchOf(p.getUniqueId());
        if (m == null) return;
        if (m.state() == MatchState.PREP) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        DuelMatch vm = runner.matchOf(victim.getUniqueId());
        if (vm == null) return;
        if (vm.state() == MatchState.PREP) {
            e.setCancelled(true);
            return;
        }
        if (e.getDamager() instanceof Player attacker) {
            DuelMatch am = runner.matchOf(attacker.getUniqueId());
            if (am == null || !am.id().equals(vm.id())) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        DuelMatch m = runner.matchOf(e.getPlayer().getUniqueId());
        if (m == null || m.state() != MatchState.PREP) return;
        if (e.getFrom().getX() != e.getTo().getX()
            || e.getFrom().getZ() != e.getTo().getZ()
            || e.getFrom().getY() != e.getTo().getY()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        DuelMatch m = runner.matchOf(e.getPlayer().getUniqueId());
        if (m == null) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        DuelMatch m = runner.matchOf(p.getUniqueId());
        if (m == null) return;
        e.setFoodLevel(20);
        p.setSaturation(0f);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        runner.handleDisconnect(e.getPlayer());
    }
}
