package com.samarth.duels.match;

import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.Kit;
import com.samarth.duels.kit.KitRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;
import java.time.Duration;

/**
 * Runs the lifecycle of a single duel: save inventories, teleport, apply kit,
 * freeze countdown, fight, round resets on kill, end + restore.
 *
 * Designed for one arena at a time. If both fighters need to share the same arena
 * with another duel, the second duel queues (returns false from start()).
 */
public final class MatchRunner {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final DuelsConfig config;
    private final KitRegistry kits;

    private final Map<UUID, DuelMatch> matchByPlayer = new HashMap<>();
    @Nullable private DuelMatch arenaInUse;
    private final List<PendingDuel> waiting = new ArrayList<>();
    /** Player UUIDs that finished a match while dead — when they respawn, teleport here. */
    private final Map<UUID, Location> postMatchTeleport = new HashMap<>();

    public MatchRunner(JavaPlugin plugin, DuelsConfig config, KitRegistry kits) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
    }

    public boolean isInMatch(UUID id) { return matchByPlayer.containsKey(id); }
    public @Nullable DuelMatch matchOf(UUID id) { return matchByPlayer.get(id); }

    public boolean isArenaBusy() { return arenaInUse != null; }

    public void start(Player a, Player b, String kitName, int bestOf, boolean useTimeLimit) {
        if (!config.isArenaReady()) {
            send(a, "<red>Arena not configured. Ask an op to run /duels setarena.</red>");
            send(b, "<red>Arena not configured. Ask an op to run /duels setarena.</red>");
            return;
        }
        Kit kit = kits.get(kitName);
        if (kit == null) {
            send(a, "<red>Kit '" + kitName + "' doesn't exist.</red>");
            send(b, "<red>Kit '" + kitName + "' doesn't exist.</red>");
            return;
        }
        if (isInMatch(a.getUniqueId()) || isInMatch(b.getUniqueId())) {
            send(a, "<red>One of you is already in a duel.</red>");
            return;
        }
        if (arenaInUse != null) {
            waiting.add(new PendingDuel(a.getUniqueId(), b.getUniqueId(), kitName, bestOf, useTimeLimit));
            send(a, "<gray>Arena busy — waiting for current duel to finish. (#" + waiting.size() + " in queue)</gray>");
            send(b, "<gray>Arena busy — waiting for current duel to finish. (#" + waiting.size() + " in queue)</gray>");
            return;
        }

        DuelMatch m = new DuelMatch(a.getUniqueId(), b.getUniqueId(), kitName, bestOf, useTimeLimit);
        arenaInUse = m;
        matchByPlayer.put(a.getUniqueId(), m);
        matchByPlayer.put(b.getUniqueId(), m);
        m.setState(MatchState.PREP);

        send(a, config.msg("match-found"));
        send(b, config.msg("match-found"));

        saveAndPrepare(a, kit, config.arenaA(), m);
        saveAndPrepare(b, kit, config.arenaB(), m);

        Component prep = MM.deserialize(config.msg("match-prep"));
        Component subA = Component.text("vs " + b.getName());
        Component subB = Component.text("vs " + a.getName());
        a.showTitle(Title.title(prep, subA, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
        b.showTitle(Title.title(prep, subB, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));

        // Build and attach the per-match sidebar showing server IP and live round score.
        rebuildSidebar(m);

        scheduleFreeze(m, true);
    }

    private void saveAndPrepare(Player p, Kit kit, Location spawn, DuelMatch m) {
        PlayerInventory pi = p.getInventory();
        m.savedInventory().put(p.getUniqueId(), pi.getContents().clone());
        m.savedArmor().put(p.getUniqueId(), pi.getArmorContents().clone());
        m.savedOffhand().put(p.getUniqueId(), pi.getItemInOffHand().clone());
        m.savedLocation().put(p.getUniqueId(), p.getLocation());
        m.savedGameMode().put(p.getUniqueId(), p.getGameMode());
        prepareFighter(p, kit, spawn);
    }

    private void prepareFighter(Player p, Kit kit, Location spawn) {
        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(0f);
        p.setFireTicks(0);
        for (PotionEffect pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());
        kits.equip(kit, p);
    }

    private void scheduleFreeze(DuelMatch m, boolean initialStart) {
        int seconds = config.freezeSeconds();
        BukkitTask t = new BukkitRunnable() {
            int remaining = seconds;
            @Override public void run() {
                if (m.state() != MatchState.PREP) { cancel(); return; }
                Player a = Bukkit.getPlayer(m.playerA());
                Player b = Bukkit.getPlayer(m.playerB());
                if (a == null || b == null) { cancel(); return; }
                if (remaining <= 0) {
                    m.setState(MatchState.FIGHTING);
                    showFightTitle(a); showFightTitle(b);
                    a.playSound(a.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    b.playSound(b.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    if (initialStart) {
                        scheduleMatchTimer(m);
                        scheduleActionBarRefresh(m);
                    }
                    // Force entity tracker refresh right before fighting begins — fixes
                    // mid-match player-pos desync (swing-at-air bug) caused by chunks
                    // just having loaded after the teleport.
                    refreshPlayerVisibility(List.of(m.playerA(), m.playerB()));
                    cancel();
                    return;
                }
                showCountdownTitle(a, remaining); showCountdownTitle(b, remaining);
                a.playSound(a.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                b.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                remaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
        m.setFreezeTask(t);
    }

    private void scheduleMatchTimer(DuelMatch m) {
        if (!m.useTimeLimit()) return;
        long capTicks = (long) config.matchTimeCapSeconds() * 20L;
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.state() != MatchState.FIGHTING) return;
            UUID winner;
            if (m.roundsA() > m.roundsB()) winner = m.playerA();
            else if (m.roundsB() > m.roundsA()) winner = m.playerB();
            else winner = m.playerA(); // tie tiebreak
            broadcastPrefixed(MM.deserialize("<yellow>Match time cap reached — higher round count wins.</yellow>"));
            endMatch(m, winner);
        }, capTicks);
        m.setTimerTask(t);
    }

    private void scheduleActionBarRefresh(DuelMatch m) {
        BukkitTask t = new BukkitRunnable() {
            @Override public void run() {
                if (m.state() == MatchState.ENDED || m.state() == MatchState.PENDING) {
                    cancel();
                    return;
                }
                Player a = Bukkit.getPlayer(m.playerA());
                Player b = Bukkit.getPlayer(m.playerB());
                if (a != null) {
                    a.setFoodLevel(20);
                    a.setSaturation(0f);
                    a.sendActionBar(killBar(m.roundsA(), m.roundsB()));
                }
                if (b != null) {
                    b.setFoodLevel(20);
                    b.setSaturation(0f);
                    b.sendActionBar(killBar(m.roundsB(), m.roundsA()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        m.setActionBarTask(t);
    }

    public void onPlayerKilled(Player victim, @Nullable Player killer) {
        DuelMatch m = matchByPlayer.get(victim.getUniqueId());
        if (m == null || m.state() != MatchState.FIGHTING) return;
        UUID opponentId = m.opponentOf(victim.getUniqueId());
        boolean award = killer == null || opponentId.equals(killer.getUniqueId());
        if (!award) return;

        if (opponentId.equals(m.playerA())) m.incrementKillsA();
        else m.incrementKillsB();

        int killsToRound = config.killsPerRound();
        boolean roundOver = false;
        UUID roundWinner = null;
        if (m.killsA() >= killsToRound) {
            m.incrementRoundsA();
            roundWinner = m.playerA();
            roundOver = true;
        } else if (m.killsB() >= killsToRound) {
            m.incrementRoundsB();
            roundWinner = m.playerB();
            roundOver = true;
        }

        if (roundOver) {
            // Sound effects: levelup for round winner, low bass for loser
            Player rw = Bukkit.getPlayer(roundWinner);
            Player rl = Bukkit.getPlayer(m.opponentOf(roundWinner));
            if (rw != null) rw.playSound(rw.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            if (rl != null) rl.playSound(rl.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            // Refresh sidebar with new round score
            rebuildSidebar(m);
            int needed = m.roundsNeeded();
            if (m.roundsA() >= needed) { endMatch(m, m.playerA()); return; }
            if (m.roundsB() >= needed) { endMatch(m, m.playerB()); return; }
            // Reset for next round
            m.setState(MatchState.PREP);
            m.resetKills();
        }
    }

    public @Nullable Location respawnLocationFor(Player victim) {
        DuelMatch m = matchByPlayer.get(victim.getUniqueId());
        if (m == null) return null;
        if (m.state() == MatchState.ENDED || m.state() == MatchState.PENDING) return null;
        return victim.getUniqueId().equals(m.playerA()) ? config.arenaA() : config.arenaB();
    }

    public void onPostRespawn(Player p) {
        DuelMatch m = matchByPlayer.get(p.getUniqueId());
        if (m == null) return;
        if (m.state() == MatchState.PREP) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> resetRound(m), 1L);
        }
    }

    private void resetRound(DuelMatch m) {
        if (m.state() != MatchState.PREP) return;
        Player a = Bukkit.getPlayer(m.playerA());
        Player b = Bukkit.getPlayer(m.playerB());
        if (a == null || b == null) return;
        Kit kit = kits.get(m.kitName());
        if (kit == null) return;
        prepareFighter(a, kit, config.arenaA());
        prepareFighter(b, kit, config.arenaB());
        Component prep = MM.deserialize(config.msg("match-prep"));
        a.showTitle(Title.title(prep, Component.text("Next round…"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
        b.showTitle(Title.title(prep, Component.text("Next round…"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
        scheduleFreeze(m, false);
    }

    private void endMatch(DuelMatch m, @Nullable UUID winnerId) {
        if (m.state() == MatchState.ENDED) return;
        m.setState(MatchState.ENDED);
        cancelTask(m.freezeTask()); m.setFreezeTask(null);
        cancelTask(m.timerTask()); m.setTimerTask(null);
        cancelTask(m.actionBarTask()); m.setActionBarTask(null);

        UUID loserId = winnerId == null ? null : (winnerId.equals(m.playerA()) ? m.playerB() : m.playerA());
        Player a = Bukkit.getPlayer(m.playerA());
        Player b = Bukkit.getPlayer(m.playerB());

        // Broadcast score
        if (winnerId != null && loserId != null) {
            int wScore = winnerId.equals(m.playerA()) ? m.roundsA() : m.roundsB();
            int lScore = winnerId.equals(m.playerA()) ? m.roundsB() : m.roundsA();
            broadcastPrefixed(MM.deserialize(config.msg("match-result-broadcast"),
                Placeholder.parsed("winner", nameOf(winnerId)),
                Placeholder.parsed("loser", nameOf(loserId)),
                Placeholder.parsed("wscore", String.valueOf(wScore)),
                Placeholder.parsed("lscore", String.valueOf(lScore))));
        }

        if (a != null) finalizePlayer(a, m.playerA().equals(winnerId), m);
        if (b != null) finalizePlayer(b, m.playerB().equals(winnerId), m);

        matchByPlayer.remove(m.playerA());
        matchByPlayer.remove(m.playerB());
        arenaInUse = null;

        // Entity tracker refresh, like Tourney teardown — prevents post-match desync
        refreshPlayerVisibility(List.of(m.playerA(), m.playerB()));

        // Pump the waiting queue (if any other duels were waiting on this arena)
        Bukkit.getScheduler().runTaskLater(plugin, this::startNextWaiting, 60L);
    }

    private void finalizePlayer(Player p, boolean won, DuelMatch m) {
        if (won) {
            p.showTitle(Title.title(MM.deserialize(config.msg("match-victory")), Component.empty()));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            p.showTitle(Title.title(MM.deserialize(config.msg("match-defeat")), Component.empty()));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        // Detach sidebar (set back to server-wide main scoreboard)
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        // Compute destination: lobby if configured, otherwise pre-duel location
        Location lobby = config.lobby();
        Location savedLoc = m.savedLocation().get(p.getUniqueId());
        Location destination = lobby != null ? lobby : savedLoc;

        // If the player is dead (typical loser, or mutual-kill winner), Bukkit silently
        // drops the teleport. Defer to respawn handling instead.
        if (p.isDead()) {
            if (destination != null) postMatchTeleport.put(p.getUniqueId(), destination);
            // Restore the player's saved inventory/mode in 1 tick — by then they should
            // have been auto-respawned by the death listener.
            DuelMatch matchRef = m;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (p.isDead()) p.spigot().respawn();
                restoreSavedLoadout(p, matchRef);
            }, 6L);
            return;
        }

        // Alive — restore immediately.
        restoreSavedLoadout(p, m);
        if (destination != null) p.teleport(destination);
    }

    private void restoreSavedLoadout(Player p, DuelMatch m) {
        ItemStack[] inv = m.savedInventory().get(p.getUniqueId());
        ItemStack[] armor = m.savedArmor().get(p.getUniqueId());
        ItemStack offhand = m.savedOffhand().get(p.getUniqueId());
        GameMode mode = m.savedGameMode().get(p.getUniqueId());
        PlayerInventory pi = p.getInventory();
        pi.clear();
        if (inv != null) pi.setContents(inv);
        if (armor != null) pi.setArmorContents(armor);
        if (offhand != null) pi.setItemInOffHand(offhand);
        if (mode != null) p.setGameMode(mode);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        for (PotionEffect pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());
        p.sendActionBar(Component.empty());
    }

    /** Called from MatchListener.onRespawn to recover the pending lobby teleport, if any. */
    public @Nullable Location consumePostMatchTeleport(UUID id) {
        return postMatchTeleport.remove(id);
    }

    public void handleDisconnect(Player p) {
        DuelMatch m = matchByPlayer.get(p.getUniqueId());
        if (m == null) return;
        // Auto-forfeit
        UUID winner = p.getUniqueId().equals(m.playerA()) ? m.playerB() : m.playerA();
        broadcastPrefixed(MM.deserialize("<gray><player> disconnected — auto-forfeit.</gray>",
            Placeholder.parsed("player", p.getName())));
        endMatch(m, winner);
    }

    private void startNextWaiting() {
        if (arenaInUse != null || waiting.isEmpty()) return;
        PendingDuel next = waiting.remove(0);
        Player a = Bukkit.getPlayer(next.a);
        Player b = Bukkit.getPlayer(next.b);
        if (a == null || b == null) {
            startNextWaiting();
            return;
        }
        start(a, b, next.kit, next.bestOf, next.useTimeLimit);
    }

    private void refreshPlayerVisibility(List<UUID> participants) {
        if (participants.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (UUID id : participants) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.equals(p)) continue;
                    viewer.hidePlayer(plugin, p);
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : participants) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        if (viewer.equals(p)) continue;
                        viewer.showPlayer(plugin, p);
                    }
                }
            }, 10L);
        }, 10L);
    }

    // ----- sidebar scoreboard -----

    /** Build/refresh the duel sidebar (server IP + live round score) and attach to both fighters. */
    private void rebuildSidebar(DuelMatch m) {
        Scoreboard sb = m.sidebarScoreboard();
        boolean firstBuild = false;
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            sb.registerNewObjective("pvptl_duel", Criteria.DUMMY,
                Component.text("PvPTL Duels").color(NamedTextColor.GOLD));
            m.setSidebarScoreboard(sb);
            firstBuild = true;
        }
        Objective obj = sb.getObjective("pvptl_duel");
        if (obj == null) return;
        if (firstBuild) {
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            // Hide the score numbers next to each line (Paper 1.20.4+).
            try {
                obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
            } catch (Throwable ignored) {
                // Older Paper — numbers will be visible; acceptable.
            }
        }
        for (String entry : new ArrayList<>(sb.getEntries())) {
            sb.resetScores(entry);
        }

        String aName = nameOf(m.playerA());
        String bName = nameOf(m.playerB());
        int needed = m.roundsNeeded();
        String ip = config.serverIp();

        // Score numbers determine vertical order (higher score = top).
        setLine(obj, "§7Server", 7);
        setLine(obj, "§b" + ip, 6);
        setLine(obj, "§r", 5);
        setLine(obj, "§b" + truncate(aName, 14) + " §8: §f" + m.roundsA(), 4);
        setLine(obj, "§c" + truncate(bName, 14) + " §8: §f" + m.roundsB(), 3);
        setLine(obj, "§l", 2);
        setLine(obj, "§7First to §f" + needed, 1);

        Player a = Bukkit.getPlayer(m.playerA());
        Player b = Bukkit.getPlayer(m.playerB());
        if (a != null) a.setScoreboard(sb);
        if (b != null) b.setScoreboard(sb);
    }

    private static void setLine(Objective obj, String entry, int scoreValue) {
        Score s = obj.getScore(entry);
        s.setScore(scoreValue);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ----- helpers -----

    private void showFightTitle(Player p) {
        Component fight = MM.deserialize(config.msg("match-fight"));
        p.showTitle(Title.title(fight, Component.empty(), Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(800), Duration.ofMillis(200))));
    }

    private void showCountdownTitle(Player p, int seconds) {
        p.showTitle(Title.title(Component.text(String.valueOf(seconds)),
            MM.deserialize("<gray>Get ready</gray>"),
            Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(700), Duration.ofMillis(150))));
    }

    private Component killBar(int you, int opp) {
        return MM.deserialize(config.msg("kill-actionbar"),
            Placeholder.parsed("you", String.valueOf(you)),
            Placeholder.parsed("opp", String.valueOf(opp)));
    }

    private void send(Player p, String msg) {
        p.sendMessage(MM.deserialize(config.prefix()).append(MM.deserialize(msg)));
    }

    private void broadcastPrefixed(Component c) {
        Bukkit.broadcast(MM.deserialize(config.prefix()).append(c));
    }

    private static String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private void cancelTask(@Nullable BukkitTask t) {
        if (t != null) try { t.cancel(); } catch (IllegalStateException ignored) {}
    }

    private static final class PendingDuel {
        final UUID a, b;
        final String kit;
        final int bestOf;
        final boolean useTimeLimit;
        PendingDuel(UUID a, UUID b, String kit, int bestOf, boolean useTimeLimit) {
            this.a = a; this.b = b; this.kit = kit; this.bestOf = bestOf; this.useTimeLimit = useTimeLimit;
        }
    }
}
