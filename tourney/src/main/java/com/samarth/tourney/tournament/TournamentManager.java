package com.samarth.tourney.tournament;

import com.samarth.tourney.config.TourneyConfig;
import com.samarth.tourney.kit.KitService;
import com.samarth.tourney.persistence.StateStore;
import com.samarth.tourney.spectate.SpectatorService;
import com.samarth.tourney.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

/**
 * The brain. One singleton instance owns the active tournament's lifecycle.
 *
 * State machine:
 *   NONE → JOINING (after /tourney start)
 *   JOINING → RUNNING (when join window closes with enough players)
 *   JOINING → NONE  (cancelled or not enough players)
 *   RUNNING → ENDED (champion crowned)
 *   ENDED → NONE  (cleanup)
 */
public final class TournamentManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final TourneyConfig config;
    private final KitService kits;
    private final SpectatorService spec;
    private final Hud hud;
    private final StateStore store;
    private final Random rng = new Random();

    @Nullable private Tournament active;
    @Nullable private BukkitTask joinWindowTask;
    private final Map<UUID, BukkitTask> graceTasks = new HashMap<>();
    private final Set<UUID> savedToDisk = new HashSet<>();
    /** Lobby destination queued for a player who finished a match while dead. Consumed on respawn. */
    private final Map<UUID, Location> postMatchTeleport = new HashMap<>();

    public TournamentManager(JavaPlugin plugin, TourneyConfig config, KitService kits,
                              SpectatorService spec, Hud hud, StateStore store) {
        this.plugin = plugin;
        this.config = config;
        this.kits = kits;
        this.spec = spec;
        this.hud = hud;
        this.store = store;
    }

    public @Nullable Tournament active() { return active; }
    public boolean isActive() { return active != null && active.state() != TournamentState.NONE; }

    public boolean isJoining() { return active != null && active.state() == TournamentState.JOINING; }
    public boolean isRunning() { return active != null && active.state() == TournamentState.RUNNING; }

    public @Nullable Match matchOf(UUID id) {
        if (active == null) return null;
        return active.activeMatchesByPlayer().get(id);
    }

    // ----- public commands -----

    public void startTournament(CommandSender starter, Map<String, Integer> overrides) {
        if (isActive()) {
            send(starter, "already-running");
            return;
        }
        if (!config.isReady()) {
            starter.sendMessage(MM.deserialize(config.prefix() +
                "<red>Setup incomplete. Run </red><yellow>/tourney setup</yellow><red> first.</red>"));
            return;
        }

        Tournament t = new Tournament();
        t.setState(TournamentState.JOINING);
        // Apply per-tournament overrides (clamped to sane ranges), falling back to config defaults.
        t.setJoinWindowSeconds(clamp(overrides.getOrDefault("join", config.joinWindowSeconds()), 10, 3600));
        t.setKillsToWin(clamp(overrides.getOrDefault("rounds", config.killsToWin()), 1, 100));
        t.setFreezeSeconds(clamp(overrides.getOrDefault("freeze", config.freezeSeconds()), 0, 30));
        t.setMatchTimeCapSeconds(clamp(overrides.getOrDefault("cap", config.matchTimeCapSeconds()), 30, 3600));
        long joinSec = t.joinWindowSeconds();
        t.setJoinDeadlineMillis(System.currentTimeMillis() + joinSec * 1000L);
        active = t;

        // Log the effective settings so staff can see what's in play
        starter.sendMessage(prefixed(MM.deserialize(
            "<gray>Settings — join: <yellow><join>s</yellow>, rounds: <yellow><rounds></yellow>, freeze: <yellow><freeze>s</yellow>, cap: <yellow><cap>s</yellow></gray>",
            Placeholder.parsed("join", String.valueOf(t.joinWindowSeconds())),
            Placeholder.parsed("rounds", String.valueOf(t.killsToWin())),
            Placeholder.parsed("freeze", String.valueOf(t.freezeSeconds())),
            Placeholder.parsed("cap", String.valueOf(t.matchTimeCapSeconds())))));

        // Auto-join the starter if a player
        if (starter instanceof Player sp) {
            t.players().add(sp.getUniqueId());
        }

        Component broadcast = MM.deserialize(config.msg("join-broadcast"),
            Placeholder.parsed("time", Hud.formatMmSs(joinSec)));
        Bukkit.broadcast(prefixed(broadcast));

        hud.showJoinBarToAll(joinSec, t.players().size());

        joinWindowTask = new BukkitRunnable() {
            @Override public void run() {
                if (active == null || active.state() != TournamentState.JOINING) {
                    cancel();
                    return;
                }
                long remainingMs = active.joinDeadlineMillis() - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    cancel();
                    joinWindowTask = null;
                    onJoinWindowEnd();
                    return;
                }
                long remainingSec = Math.max(0, remainingMs / 1000);
                hud.updateJoinBar(joinSec, remainingSec, active.players().size());
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void joinTournament(Player p) {
        if (!isJoining()) {
            send(p, "no-active-tournament");
            return;
        }
        if (active.players().contains(p.getUniqueId())) {
            p.sendMessage(prefixed(MM.deserialize("<yellow>You are already in.</yellow>")));
            return;
        }
        if (active.players().size() >= config.maxPlayers()) {
            p.sendMessage(prefixed(MM.deserialize("<red>Tournament is full.</red>")));
            return;
        }
        active.players().add(p.getUniqueId());
        p.sendMessage(prefixed(MM.deserialize(config.msg("joined"),
            Placeholder.parsed("count", String.valueOf(active.players().size())))));
        hud.showPlayerOnJoinBar(p);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
    }

    public void leaveTournament(Player p) {
        if (!isJoining()) return;
        if (active.players().remove(p.getUniqueId())) {
            p.sendMessage(prefixed(MM.deserialize(config.msg("left"))));
        }
    }

    public void cancelTournament(CommandSender sender) {
        if (!isActive()) {
            send(sender, "no-active-tournament");
            return;
        }
        teardown(true);
        sender.sendMessage(prefixed(MM.deserialize("<yellow>Tournament cancelled.</yellow>")));
    }

    // ----- internal lifecycle -----

    private void onJoinWindowEnd() {
        if (active == null) return;
        // Filter out anyone offline
        active.players().removeIf(id -> Bukkit.getPlayer(id) == null);
        if (active.players().size() < config.minPlayers()) {
            broadcastPrefixed(MM.deserialize(config.msg("not-enough-players")));
            teardown(true);
            return;
        }

        // Save inventories before they get clobbered
        for (UUID id : active.players()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                store.save(p);
                savedToDisk.add(id);
            }
        }

        // Build bracket
        Bracket b = Bracket.build(active.players(), rng);
        active.setBracket(b);
        active.setState(TournamentState.RUNNING);

        broadcastPrefixed(MM.deserialize(config.msg("bracket-built"),
            Placeholder.parsed("count", String.valueOf(active.players().size()))));
        hud.hideJoinBar();

        dispatchMatches();
    }

    private void dispatchMatches() {
        if (active == null || active.bracket() == null) return;
        Bracket bracket = active.bracket();
        bracket.propagate(); // resolve byes if any new ones surfaced

        if (bracket.isComplete()) {
            announceWinner(bracket.champion());
            return;
        }

        // For each free arena, pull next ready match
        for (Map.Entry<String, Arena> e : config.arenas().entrySet()) {
            String arenaName = e.getKey();
            if (active.activeMatchesByArena().containsKey(arenaName)) continue;
            BracketMatch bm = bracket.nextReadyMatch();
            if (bm == null) break;
            bm.setStarted(true);
            startMatch(e.getValue(), bm);
        }

        // If no matches dispatched and none running, the bracket might be waiting on
        // matches in higher rounds (shouldn't happen with single-elim) — log it.
        if (active.activeMatchesByArena().isEmpty() && bracket.nextReadyMatch() == null) {
            if (!bracket.isComplete()) {
                plugin.getLogger().warning("Bracket stalled with no ready matches and no active matches.");
            }
        }
    }

    private void startMatch(Arena arena, BracketMatch bm) {
        UUID aId = bm.playerA();
        UUID bId = bm.playerB();
        if (aId == null || bId == null) {
            plugin.getLogger().warning("startMatch called on bracket match with missing players");
            return;
        }
        Player a = Bukkit.getPlayer(aId);
        Player b = Bukkit.getPlayer(bId);
        if (a == null || b == null) {
            // Auto-forfeit to whichever is online
            UUID winner = a != null ? aId : (b != null ? bId : aId);
            bm.setWinner(winner);
            bm.setPlayed(true);
            active.bracket().propagate();
            broadcastPrefixed(MM.deserialize("<gray>One player offline; auto-forfeit recorded.</gray>"));
            Bukkit.getScheduler().runTaskLater(plugin, this::dispatchMatches, 20L);
            return;
        }

        Match m = new Match(bm, arena, aId, bId);
        active.activeMatchesByArena().put(arena.name(), m);
        active.activeMatchesByPlayer().put(aId, m);
        active.activeMatchesByPlayer().put(bId, m);
        m.setState(MatchState.PREP);

        // Place fighters
        prepareFighter(a, arena.spawnA());
        prepareFighter(b, arena.spawnB());

        hud.prepTitle(a, b.getName());
        hud.prepTitle(b, a.getName());

        // Refresh entity tracker right after the teleport — gives clients the freeze
        // countdown to settle on accurate opponent positions before swings start.
        refreshPlayerVisibility(List.of(m.playerA(), m.playerB()));

        scheduleFreeze(m, true);
    }

    private void prepareFighter(Player p, Location spawn) {
        if (spawn.getWorld() != null) {
            try { spawn.getChunk(); } catch (Throwable ignored) {}
        }
        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(0f);
        p.setFireTicks(0);
        p.setExp(0);
        p.setLevel(0);
        for (PotionEffect pe : p.getActivePotionEffects()) {
            p.removePotionEffect(pe.getType());
        }
        kits.apply(p);
    }

    private void scheduleFreeze(Match m, boolean initialStart) {
        int freezeSec = active != null ? active.freezeSeconds() : config.freezeSeconds();
        BukkitTask t = new BukkitRunnable() {
            int remaining = freezeSec;
            @Override public void run() {
                if (active == null || m.state() != MatchState.PREP) {
                    cancel();
                    return;
                }
                Player a = Bukkit.getPlayer(m.playerA());
                Player b = Bukkit.getPlayer(m.playerB());
                if (a == null || b == null) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    m.setState(MatchState.FIGHTING);
                    hud.fightTitle(a);
                    hud.fightTitle(b);
                    a.playSound(a.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    b.playSound(b.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    if (initialStart) {
                        m.setStartMillis(System.currentTimeMillis());
                        hud.killScore(a, 0, 0);
                        hud.killScore(b, 0, 0);
                        scheduleMatchTimer(m);
                        scheduleActionBarRefresh(m);
                    }
                    cancel();
                    return;
                }
                hud.countdownTitle(a, remaining);
                hud.countdownTitle(b, remaining);
                a.playSound(a.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                b.playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                remaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
        m.setFreezeTask(t);
    }

    /** Reset both players to spawn points and run a fresh freeze countdown after a kill. */
    private void resetRound(Match m) {
        if (m.state() != MatchState.PREP) return;
        Player a = Bukkit.getPlayer(m.playerA());
        Player b = Bukkit.getPlayer(m.playerB());
        if (a == null || b == null) return; // disconnect handled elsewhere
        prepareFighter(a, m.arena().spawnA());
        prepareFighter(b, m.arena().spawnB());
        hud.prepTitle(a, b.getName());
        hud.prepTitle(b, a.getName());
        scheduleFreeze(m, false);
    }

    private void scheduleMatchTimer(Match m) {
        int capSec = active != null ? active.matchTimeCapSeconds() : config.matchTimeCapSeconds();
        long capTicks = (long) capSec * 20L;
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.state() != MatchState.FIGHTING) return;
            UUID winner;
            if (m.killsA() > m.killsB()) winner = m.playerA();
            else if (m.killsB() > m.killsA()) winner = m.playerB();
            else winner = m.playerA(); // pure-tie tiebreaker: arbitrary, give it to A
            broadcastPrefixed(MM.deserialize("<yellow>Match time cap reached — higher kills wins.</yellow>"));
            endMatch(m, winner);
        }, capTicks);
        m.setTimerTask(t);
    }

    private void scheduleActionBarRefresh(Match m) {
        BukkitTask t = new BukkitRunnable() {
            @Override public void run() {
                // Keep refreshing across PREP (between rounds) too — score is meaningful then.
                if (m.state() == MatchState.ENDED || m.state() == MatchState.PENDING) {
                    cancel();
                    return;
                }
                Player a = Bukkit.getPlayer(m.playerA());
                Player b = Bukkit.getPlayer(m.playerB());
                if (a != null) {
                    a.setFoodLevel(20);
                    a.setSaturation(0f);
                    hud.killScore(a, m.killsA(), m.killsB());
                }
                if (b != null) {
                    b.setFoodLevel(20);
                    b.setSaturation(0f);
                    hud.killScore(b, m.killsB(), m.killsA());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        m.setActionBarTask(t);
    }

    /** Called from death listener when a match participant dies. */
    public void onPlayerKilled(Player victim, @Nullable Player killer) {
        if (active == null) return;
        Match m = active.activeMatchesByPlayer().get(victim.getUniqueId());
        if (m == null) return;
        if (m.state() != MatchState.FIGHTING) return;
        // Award the kill to the opponent if they're the killer (or no killer = environment death = opponent gets it
        // because the only way to die in arena is the opponent or void).
        UUID opponentId = m.opponentOf(victim.getUniqueId());
        boolean award = killer == null || opponentId.equals(killer.getUniqueId());
        if (!award) return;

        if (opponentId.equals(m.playerA())) m.incrementKillsA();
        else m.incrementKillsB();

        Player a = Bukkit.getPlayer(m.playerA());
        Player b = Bukkit.getPlayer(m.playerB());
        if (a != null) hud.killScore(a, m.killsA(), m.killsB());
        if (b != null) hud.killScore(b, m.killsB(), m.killsA());

        int target = active != null ? active.killsToWin() : config.killsToWin();
        // Refresh spectator sidebar if anyone's watching this match.
        if (m.sidebarScoreboard() != null) {
            rebuildSidebar(m);
        }
        if (m.killsA() >= target) {
            endMatch(m, m.playerA());
        } else if (m.killsB() >= target) {
            endMatch(m, m.playerB());
        } else {
            // Round won but match continues — reset both players to spawns with a fresh freeze.
            // Actual reset happens after the victim's respawn fires (see onPostRespawn).
            m.setState(MatchState.PREP);
        }
    }

    public Location respawnLocationFor(Player victim) {
        Match m = matchOf(victim.getUniqueId());
        if (m == null) return null;
        if (m.state() == MatchState.ENDED || m.state() == MatchState.PENDING) return null;
        return victim.getUniqueId().equals(m.playerA()) ? m.arena().spawnA() : m.arena().spawnB();
    }

    public void onPostRespawn(Player p) {
        Match m = matchOf(p.getUniqueId());
        if (m == null) return;
        // If we're between rounds, kick off the round reset (teleport both, refill kits, countdown).
        if (m.state() == MatchState.PREP) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> resetRound(m), 1L);
        }
    }

    private void endMatch(Match m, @Nullable UUID winnerId) {
        if (m.state() == MatchState.ENDED) return;
        m.setState(MatchState.ENDED);
        cancelTask(m.freezeTask()); m.setFreezeTask(null);
        cancelTask(m.timerTask()); m.setTimerTask(null);
        cancelTask(m.actionBarTask()); m.setActionBarTask(null);

        UUID loserId = winnerId == null ? null : (winnerId.equals(m.playerA()) ? m.playerB() : m.playerA());

        // Broadcast the final match score to chat
        if (winnerId != null && loserId != null) {
            int wScore = winnerId.equals(m.playerA()) ? m.killsA() : m.killsB();
            int lScore = winnerId.equals(m.playerA()) ? m.killsB() : m.killsA();
            broadcastPrefixed(MM.deserialize(config.msg("match-result-broadcast"),
                Placeholder.parsed("winner", nameOf(winnerId)),
                Placeholder.parsed("loser", nameOf(loserId)),
                Placeholder.parsed("wscore", String.valueOf(wScore)),
                Placeholder.parsed("lscore", String.valueOf(lScore))));
        }

        Player a = Bukkit.getPlayer(m.playerA());
        Player b = Bukkit.getPlayer(m.playerB());

        if (a != null) postMatchPlayer(a, m.playerA().equals(winnerId), loserId != null && m.playerA().equals(loserId));
        if (b != null) postMatchPlayer(b, m.playerB().equals(winnerId), loserId != null && m.playerB().equals(loserId));

        active.activeMatchesByArena().remove(m.arena().name());
        active.activeMatchesByPlayer().remove(m.playerA());
        active.activeMatchesByPlayer().remove(m.playerB());

        // NOTE: store.restoreIfPresent(loser) is intentionally NOT called here.
        // The loser is dead at this point; Bukkit silently drops teleport() on a dead
        // player, and restoreIfPresent would also delete the saved file as a side effect —
        // which is what stranded the loser at world-spawn before. postMatchPlayer above
        // now defers the lobby teleport + inventory restore to AFTER the player auto-
        // respawns (see postMatchAliveCleanup).

        // Update bracket
        if (winnerId != null) {
            BracketMatch bm = m.bracketMatch();
            bm.setWinner(winnerId);
            bm.setPlayed(true);
            active.bracket().propagate();
        }

        // Force entity-tracker refresh for both fighters so the winner doesn't see the
        // loser as "frozen" in their last-known position while they're auto-respawning.
        refreshPlayerVisibility(List.of(m.playerA(), m.playerB()));

        Bukkit.getScheduler().runTaskLater(plugin, this::dispatchMatches, 60L);
    }

    private void postMatchPlayer(Player p, boolean won, boolean lost) {
        if (won) hud.winTitle(p);
        else if (lost) hud.lossTitle(p);

        Location lobby = config.lobby();

        // Dead path: defer the teleport + inventory restore until after the auto-respawn
        // listener (5-tick) puts them back into the world. Otherwise teleport() and any
        // inventory mutation silently fail and the player ends up at world-spawn.
        if (p.isDead()) {
            if (lobby != null) postMatchTeleport.put(p.getUniqueId(), lobby);
            UUID pid = p.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = Bukkit.getPlayer(pid);
                if (player == null) return;
                if (player.isDead()) player.spigot().respawn();
                postMatchAliveCleanup(player, lost);
            }, 6L);
            return;
        }

        // Alive path: do everything inline.
        postMatchAliveCleanup(p, lost);
        if (lobby != null) p.teleport(lobby);
    }

    private void postMatchAliveCleanup(Player p, boolean lost) {
        kits.clear(p);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        for (PotionEffect pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());
        p.sendActionBar(Component.empty());
        if (lost) {
            // Eliminated players get their pre-tournament loadout back immediately so they
            // can spectate or leave without the kit. Champions hold on to the kit until
            // teardown, which restores their state alongside everyone else.
            store.restoreIfPresent(p);
            savedToDisk.remove(p.getUniqueId());
        }
    }

    /** Called from MatchListener.onRespawn — recovers the lobby destination queued at endMatch. */
    public @Nullable Location consumePostMatchTeleport(UUID id) {
        return postMatchTeleport.remove(id);
    }

    private void announceWinner(@Nullable UUID winnerId) {
        if (winnerId != null) {
            String name = Bukkit.getOfflinePlayer(winnerId).getName();
            broadcastPrefixed(MM.deserialize(config.msg("tournament-winner-broadcast"),
                Placeholder.parsed("winner", name == null ? "Champion" : name)));
            Player w = Bukkit.getPlayer(winnerId);
            if (w != null) {
                w.playSound(w.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                hud.winTitle(w);
            }
        }
        // Persist tournament-wide stats (if PvPTLStats is loaded). One row per participant,
        // with rounds_won counting how many bracket rounds they won (champion = totalRounds).
        recordTournamentStats();
        teardown(false);
    }

    private void recordTournamentStats() {
        com.samarth.stats.StatsService stats = com.samarth.tourney.stats.StatsBridge.tryGet();
        if (stats == null || active == null || active.bracket() == null) return;
        Bracket bracket = active.bracket();
        int totalRounds = bracket.totalRounds();
        java.util.Map<UUID, Integer> roundsWonByPlayer = new HashMap<>();
        for (UUID id : active.players()) roundsWonByPlayer.put(id, 0);
        for (java.util.List<BracketMatch> round : bracket.rounds()) {
            for (BracketMatch m : round) {
                if (m.played() && m.winner() != null) {
                    roundsWonByPlayer.merge(m.winner(), 1, Integer::sum);
                }
            }
        }
        long now = System.currentTimeMillis();
        UUID tournamentId = active.id();
        for (java.util.Map.Entry<UUID, Integer> e : roundsWonByPlayer.entrySet()) {
            stats.recordTournamentResult(new com.samarth.stats.model.TournamentResult(
                now, tournamentId, e.getKey(), e.getValue(), totalRounds));
        }
    }

    /** End the current tournament cleanly. forceful=true means cancellation, otherwise normal end. */
    private void teardown(boolean forceful) {
        if (active == null) return;
        // Capture before clearing — we need it for the post-teardown visibility refresh.
        List<UUID> participants = new ArrayList<>(active.players());

        // Cancel any active match tasks, restore players
        for (Match m : new ArrayList<>(active.activeMatchesByArena().values())) {
            cancelTask(m.freezeTask());
            cancelTask(m.timerTask());
            cancelTask(m.actionBarTask());
        }
        // Restore everyone with saved state
        for (UUID id : new ArrayList<>(active.players())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                store.restoreIfPresent(p);
            }
            savedToDisk.remove(id);
        }
        // Drop any spectators back to their normal state
        for (UUID spectatorId : new ArrayList<>(spec.spectators())) {
            Player viewer = Bukkit.getPlayer(spectatorId);
            if (viewer != null) spec.exit(viewer);
        }
        // Cancel join window task if still alive
        if (joinWindowTask != null) { joinWindowTask.cancel(); joinWindowTask = null; }
        // Clear grace tasks
        for (BukkitTask t : graceTasks.values()) t.cancel();
        graceTasks.clear();

        hud.hideJoinBar();
        active = null;

        // Bug fix: back-to-back teleports during teardown sometimes leave clients with stale
        // entity positions for other players (move-but-no-update on others' screens, only
        // fixed by relog). Force a Bukkit entity tracker refresh by cycling hide/show.
        refreshPlayerVisibility(participants);
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

    // ----- presence -----

    public void onPlayerQuit(Player p) {
        if (active == null) return;
        if (active.state() == TournamentState.JOINING) {
            active.players().remove(p.getUniqueId());
            return;
        }
        Match m = active.activeMatchesByPlayer().get(p.getUniqueId());
        if (m == null) return;
        m.setDisconnectedPlayer(p.getUniqueId());
        long graceTicks = (long) config.disconnectGraceSeconds() * 20L;
        UUID quitter = p.getUniqueId();
        BukkitTask t = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            graceTasks.remove(quitter);
            if (m.state() == MatchState.ENDED) return;
            UUID winner = quitter.equals(m.playerA()) ? m.playerB() : m.playerA();
            String name = Bukkit.getOfflinePlayer(quitter).getName();
            broadcastPrefixed(MM.deserialize(config.msg("forfeit"),
                Placeholder.parsed("player", name == null ? "Player" : name)));
            endMatch(m, winner);
        }, graceTicks);
        graceTasks.put(quitter, t);
    }

    public void onPlayerJoin(Player p) {
        if (active == null) return;
        BukkitTask t = graceTasks.remove(p.getUniqueId());
        if (t != null) {
            t.cancel();
            Match m = matchOf(p.getUniqueId());
            if (m != null && m.state() != MatchState.ENDED) {
                Location spawn = p.getUniqueId().equals(m.playerA()) ? m.arena().spawnA() : m.arena().spawnB();
                p.teleport(spawn);
                kits.apply(p);
                m.setDisconnectedPlayer(null);
                p.sendMessage(prefixed(MM.deserialize("<green>Reconnected to match.</green>")));
            }
        }
    }

    // ----- helpers -----

    public Component prefixed(Component msg) {
        return MM.deserialize(config.prefix()).append(msg);
    }

    public void broadcastPrefixed(Component msg) {
        Bukkit.broadcast(prefixed(msg));
    }

    private void send(CommandSender to, String msgKey) {
        to.sendMessage(prefixed(MM.deserialize(config.msg(msgKey))));
    }

    private static String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ----- spectator sidebar -----

    /** Build (or refresh) the live-score sidebar scoreboard for a match. */
    private void rebuildSidebar(Match m) {
        Scoreboard sb = m.sidebarScoreboard();
        boolean firstBuild = false;
        if (sb == null) {
            sb = Bukkit.getScoreboardManager().getNewScoreboard();
            sb.registerNewObjective("tourney", Criteria.DUMMY,
                Component.text("Match").color(NamedTextColor.GOLD));
            m.setSidebarScoreboard(sb);
            firstBuild = true;
        }
        Objective obj = sb.getObjective("tourney");
        if (obj == null) return;
        if (firstBuild) obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Reset previous entries (rebuild on every kill, low frequency)
        for (String entry : new ArrayList<>(sb.getEntries())) {
            sb.resetScores(entry);
        }

        String aName = nameOf(m.playerA());
        String bName = nameOf(m.playerB());
        int target = active != null ? active.killsToWin() : config.killsToWin();

        // Score numbers determine vertical order, top=highest.
        setLine(obj, "§7Arena: §f" + m.arena().name(), 6);
        setLine(obj, "§r", 5); // blank spacer (unique entry "§r")
        setLine(obj, "§b" + truncate(aName, 14) + " §8: §f" + m.killsA(), 4);
        setLine(obj, "§c" + truncate(bName, 14) + " §8: §f" + m.killsB(), 3);
        setLine(obj, "§l", 2); // blank spacer (unique entry "§l")
        setLine(obj, "§7First to §f" + target, 1);
    }

    private static void setLine(Objective obj, String entry, int score) {
        Score s = obj.getScore(entry);
        s.setScore(score);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Public entry: begin spectating a target player and attach the sidebar. */
    public boolean startSpectating(Player viewer, Player target) {
        Match m = matchOf(target.getUniqueId());
        if (m == null) return false;
        spec.enter(viewer, target);
        rebuildSidebar(m);
        Scoreboard sb = m.sidebarScoreboard();
        if (sb != null) viewer.setScoreboard(sb);
        return true;
    }

    /** Public entry: stop spectating and reset the viewer's scoreboard. */
    public void stopSpectating(Player viewer) {
        if (!spec.isSpectator(viewer.getUniqueId())) return;
        spec.exit(viewer);
    }

    private void cancelTask(@Nullable BukkitTask t) {
        if (t != null) {
            try { t.cancel(); } catch (IllegalStateException ignored) {}
        }
    }

    /** Used by GUIs to find which match a clicked head belongs to. */
    public @Nullable Match findMatchByPlayer(UUID id) {
        return matchOf(id);
    }

    /** Iterator for active matches, copy-safe. */
    public Iterator<Match> activeMatches() {
        if (active == null) return new ArrayList<Match>().iterator();
        return new ArrayList<>(active.activeMatchesByArena().values()).iterator();
    }
}
