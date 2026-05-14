package com.samarth.duels.match;

import com.samarth.duels.config.Arena;
import com.samarth.duels.config.DuelsConfig;
import com.samarth.duels.kit.KitsBridge;
import com.samarth.kits.KitService;
import java.time.Duration;
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

/**
 * Runs the lifecycle of a single duel: save inventories, teleport, apply kit,
 * freeze countdown, fight, round resets on team elimination, end + restore.
 *
 * Supports N-vs-N teams. A "round" ends when one entire team is dead in the round;
 * the surviving team gets a round win. First-to-N round wins takes the match.
 *
 * Designed for one arena at a time. If both teams need to share the same arena
 * with another duel, the second duel queues (waits until current ends).
 */
public final class MatchRunner {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final DuelsConfig config;
    /** Set via setter from DuelsPlugin after both services are constructed. */
    @Nullable private com.samarth.duels.queue.QueueService queues;

    private final Map<UUID, DuelMatch> matchByPlayer = new HashMap<>();
    /** Currently-running match per arena ID. An entry means that arena is occupied. */
    private final Map<String, DuelMatch> activeByArena = new HashMap<>();
    private final List<PendingDuel> waiting = new ArrayList<>();
    /** Player UUIDs that finished a match while dead — when they respawn, teleport here. */
    private final Map<UUID, Location> postMatchTeleport = new HashMap<>();

    public MatchRunner(JavaPlugin plugin, DuelsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Wire-up done after construction so MatchRunner can hand the requeue item to players. */
    public void setQueues(com.samarth.duels.queue.QueueService queues) {
        this.queues = queues;
    }

    public boolean isInMatch(UUID id) { return matchByPlayer.containsKey(id); }
    public @Nullable DuelMatch matchOf(UUID id) { return matchByPlayer.get(id); }

    public boolean isArenaBusy() {
        // "Any arena busy?" — kept for the /duels info display.
        return !activeByArena.isEmpty();
    }

    public int activeMatchCount() { return activeByArena.size(); }
    public int waitingMatchCount() { return waiting.size(); }

    /** 1v1 convenience — wraps single players into single-element teams. */
    public void start(Player a, Player b, String kitName, int firstTo, boolean useTimeLimit) {
        start(List.of(a), List.of(b), kitName, firstTo, useTimeLimit, false);
    }

    /** 1v1 ranked-aware convenience. */
    public void start(Player a, Player b, String kitName, int firstTo,
                      boolean useTimeLimit, boolean ranked) {
        start(List.of(a), List.of(b), kitName, firstTo, useTimeLimit, ranked);
    }

    public void start(List<Player> teamA, List<Player> teamB,
                      String kitName, int firstTo, boolean useTimeLimit) {
        start(teamA, teamB, kitName, firstTo, useTimeLimit, false);
    }

    /**
     * Start a duel between two teams. Each team's slot index in its list maps to the
     * corresponding arena spawn slot (team A slot 0 → spawnsA[0], etc.).
     *
     * If multiple arenas are configured, picks the first idle arena that fits both
     * team sizes. If every arena is busy or none fit, the match queues until an
     * arena frees up.
     */
    public void start(List<Player> teamA, List<Player> teamB,
                      String kitName, int firstTo, boolean useTimeLimit, boolean ranked) {
        if (teamA.isEmpty() || teamB.isEmpty()) return;
        if (config.totalArenas() == 0) {
            broadcastTo(teamA, "<red>Arena not configured. Ask an op to run /duels setarena.</red>");
            broadcastTo(teamB, "<red>Arena not configured. Ask an op to run /duels setarena.</red>");
            return;
        }
        // Any arena that fits both team sizes? (regardless of busy state)
        Arena fittingArena = firstFittingArena(teamA.size(), teamB.size(), true);
        if (fittingArena == null) {
            String err = "<red>No arena has " + teamA.size() + " vs " + teamB.size()
                + " spawn slots. Ask an op to add more with /duels setarena.</red>";
            broadcastTo(teamA, err);
            broadcastTo(teamB, err);
            return;
        }
        KitService kits = KitsBridge.tryGet();
        if (kits == null) {
            broadcastTo(teamA, "<red>PvPTLKits not loaded — duels cannot run.</red>");
            broadcastTo(teamB, "<red>PvPTLKits not loaded — duels cannot run.</red>");
            return;
        }
        if (kits.get(kitName) == null) {
            broadcastTo(teamA, "<red>Kit '" + kitName + "' doesn't exist.</red>");
            broadcastTo(teamB, "<red>Kit '" + kitName + "' doesn't exist.</red>");
            return;
        }
        for (Player p : teamA) {
            if (isInMatch(p.getUniqueId())) {
                broadcastTo(teamA, "<red>" + p.getName() + " is already in a duel.</red>");
                broadcastTo(teamB, "<red>" + p.getName() + " is already in a duel.</red>");
                return;
            }
        }
        for (Player p : teamB) {
            if (isInMatch(p.getUniqueId())) {
                broadcastTo(teamA, "<red>" + p.getName() + " is already in a duel.</red>");
                broadcastTo(teamB, "<red>" + p.getName() + " is already in a duel.</red>");
                return;
            }
        }
        // Pick a FREE arena that fits.
        Arena arena = firstFreeArena(teamA.size(), teamB.size());
        if (arena == null) {
            List<UUID> idsA = toIds(teamA);
            List<UUID> idsB = toIds(teamB);
            waiting.add(new PendingDuel(idsA, idsB, kitName, firstTo, useTimeLimit, ranked));
            String waitMsg = "<gray>All arenas busy — waiting in queue. (#"
                + waiting.size() + ")</gray>";
            broadcastTo(teamA, waitMsg);
            broadcastTo(teamB, waitMsg);
            return;
        }

        DuelMatch m = new DuelMatch(toIds(teamA), toIds(teamB), kitName, firstTo,
            useTimeLimit, arena.id(), ranked);
        activeByArena.put(arena.id(), m);
        for (Player p : teamA) matchByPlayer.put(p.getUniqueId(), m);
        for (Player p : teamB) matchByPlayer.put(p.getUniqueId(), m);
        m.setState(MatchState.PREP);

        broadcastTo(teamA, config.msg("match-found"));
        broadcastTo(teamB, config.msg("match-found"));

        List<Location> spawnsA = arena.spawnsA();
        List<Location> spawnsB = arena.spawnsB();
        // Save + prepare each fighter on their slot's spawn.
        for (int i = 0; i < teamA.size(); i++) {
            saveAndPrepare(teamA.get(i), kitName, spawnsA.get(i), m);
        }
        for (int i = 0; i < teamB.size(); i++) {
            saveAndPrepare(teamB.get(i), kitName, spawnsB.get(i), m);
        }

        Component prep = MM.deserialize(config.msg("match-prep"));
        String aLabel = labelFor(teamA);
        String bLabel = labelFor(teamB);
        for (Player p : teamA) {
            p.showTitle(Title.title(prep, Component.text("vs " + bLabel),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
        }
        for (Player p : teamB) {
            p.showTitle(Title.title(prep, Component.text("vs " + aLabel),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
        }

        rebuildSidebar(m);
        refreshPlayerVisibility(m.allPlayers());
        scheduleFreeze(m, true);
    }

    /** First arena (in config order) that fits both team sizes. Optionally include busy ones. */
    private @Nullable Arena firstFittingArena(int teamASize, int teamBSize, boolean includeBusy) {
        for (Arena a : config.arenas()) {
            if (!a.isReady() || !a.canFit(teamASize, teamBSize)) continue;
            if (!includeBusy && activeByArena.containsKey(a.id())) continue;
            return a;
        }
        return null;
    }

    private @Nullable Arena firstFreeArena(int teamASize, int teamBSize) {
        return firstFittingArena(teamASize, teamBSize, false);
    }

    private void saveAndPrepare(Player p, String kitName, Location spawn, DuelMatch m) {
        // Strip any duel-managed UI items so they don't get baked into the inventory
        // snapshot (leave-queue barrier, requeue paper, lobby pickers).
        if (queues != null) {
            queues.removeRequeueItem(p);
        }
        stripLobbyItems(p);
        PlayerInventory pi = p.getInventory();
        m.savedInventory().put(p.getUniqueId(), pi.getContents().clone());
        m.savedArmor().put(p.getUniqueId(), pi.getArmorContents().clone());
        m.savedOffhand().put(p.getUniqueId(), pi.getItemInOffHand().clone());
        m.savedLocation().put(p.getUniqueId(), p.getLocation());
        m.savedGameMode().put(p.getUniqueId(), p.getGameMode());
        prepareFighter(p, kitName, spawn);
    }

    /**
     * Strip the diamond/iron lobby swords (and any other PDC-tagged duels UI item)
     * before snapshotting. Looked up via the plugin's LobbyItems instance.
     */
    private void stripLobbyItems(Player p) {
        if (!(plugin instanceof com.samarth.duels.DuelsPlugin dp)) return;
        com.samarth.duels.lobby.LobbyItems items = dp.lobbyItems();
        if (items != null) items.remove(p);
    }

    private void prepareFighter(Player p, String kitName, Location spawn) {
        if (spawn.getWorld() != null) {
            try { spawn.getChunk(); } catch (Throwable ignored) {}
        }
        p.teleport(spawn);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(0f);
        p.setFireTicks(0);
        for (PotionEffect pe : p.getActivePotionEffects()) p.removePotionEffect(pe.getType());
        KitService kits = KitsBridge.tryGet();
        if (kits != null) kits.equip(kitName, p);
    }

    private void scheduleFreeze(DuelMatch m, boolean initialStart) {
        int seconds = config.freezeSeconds();
        BukkitTask t = new BukkitRunnable() {
            int remaining = seconds;
            @Override public void run() {
                if (m.state() != MatchState.PREP) { cancel(); return; }
                if (!hasOnlinePlayerInTeam(m.teamA()) || !hasOnlinePlayerInTeam(m.teamB())) {
                    cancel();
                    return;
                }
                if (remaining <= 0) {
                    m.setState(MatchState.FIGHTING);
                    for (UUID id : m.allPlayers()) {
                        Player p = Bukkit.getPlayer(id);
                        if (p == null) continue;
                        showFightTitle(p);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    }
                    if (initialStart) {
                        m.markStartedNow();
                        scheduleMatchTimer(m);
                        scheduleActionBarRefresh(m);
                    }
                    cancel();
                    return;
                }
                for (UUID id : m.allPlayers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    showCountdownTitle(p, remaining);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
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
            int winningTeam;
            if (m.roundsA() > m.roundsB()) winningTeam = 1;
            else if (m.roundsB() > m.roundsA()) winningTeam = 2;
            else winningTeam = 1; // tiebreak: Team A
            broadcastPrefixed(MM.deserialize("<yellow>Match time cap reached — higher round count wins.</yellow>"));
            endMatch(m, winningTeam);
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
                for (UUID id : m.teamA()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    p.setFoodLevel(20);
                    p.setSaturation(0f);
                    p.sendActionBar(killBar(m.roundsA(), m.roundsB()));
                }
                for (UUID id : m.teamB()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    p.setFoodLevel(20);
                    p.setSaturation(0f);
                    p.sendActionBar(killBar(m.roundsB(), m.roundsA()));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        m.setActionBarTask(t);
    }

    public void onPlayerKilled(Player victim, @Nullable Player killer) {
        DuelMatch m = matchByPlayer.get(victim.getUniqueId());
        if (m == null || m.state() != MatchState.FIGHTING) return;
        UUID victimId = victim.getUniqueId();
        if (m.isDeadInRound(victimId)) return; // dedupe

        int victimTeam = m.teamOf(victimId);
        if (victimTeam == 0) return;

        m.markDeadInRound(victimId);

        // Credit kill to the opposing team's tally if killer is on the opposing team.
        if (killer != null) {
            int killerTeam = m.teamOf(killer.getUniqueId());
            if (killerTeam != 0 && killerTeam != victimTeam) {
                if (killerTeam == 1) m.incrementKillsA();
                else m.incrementKillsB();
            }
        }

        // Round-end check: one team fully dead in round.
        boolean teamADead = m.teamAFullyDead();
        boolean teamBDead = m.teamBFullyDead();
        if (!teamADead && !teamBDead) return;

        int winningTeam;
        if (teamADead && !teamBDead) winningTeam = 2;
        else if (teamBDead && !teamADead) winningTeam = 1;
        else winningTeam = 0; // mutual KO — no round advance

        if (winningTeam == 1) m.incrementRoundsA();
        else if (winningTeam == 2) m.incrementRoundsB();

        playRoundEndSounds(m, winningTeam);
        rebuildSidebar(m);

        int needed = m.roundsNeeded();
        if (m.roundsA() >= needed) { endMatch(m, 1); return; }
        if (m.roundsB() >= needed) { endMatch(m, 2); return; }

        // Set up for next round.
        m.setState(MatchState.PREP);
        m.resetKills();
        // Give dead players ~1s to auto-respawn before we reset.
        Bukkit.getScheduler().runTaskLater(plugin, () -> resetRound(m), 20L);
    }

    private void playRoundEndSounds(DuelMatch m, int winningTeam) {
        List<UUID> winners = winningTeam == 1 ? m.teamA()
                           : winningTeam == 2 ? m.teamB()
                           : List.of();
        List<UUID> losers = winningTeam == 1 ? m.teamB()
                          : winningTeam == 2 ? m.teamA()
                          : m.allPlayers();
        for (UUID id : winners) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        for (UUID id : losers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
    }

    public @Nullable Location respawnLocationFor(Player victim) {
        DuelMatch m = matchByPlayer.get(victim.getUniqueId());
        if (m == null) return null;
        if (m.state() == MatchState.ENDED || m.state() == MatchState.PENDING) return null;
        int team = m.teamOf(victim.getUniqueId());
        if (team == 0) return null;
        Arena arena = config.arena(m.arenaId());
        if (arena == null) return null;
        List<Location> spawns = team == 1 ? arena.spawnsA() : arena.spawnsB();
        if (spawns.isEmpty()) return null;
        int slot = m.slotOf(victim.getUniqueId());
        if (slot < 0 || slot >= spawns.size()) slot = 0;
        return spawns.get(slot);
    }

    public void onPostRespawn(Player p) {
        DuelMatch m = matchByPlayer.get(p.getUniqueId());
        if (m == null) return;
        // Mid-round: surviving teammates still fighting, this player is out for the round.
        if (m.state() == MatchState.FIGHTING && m.isDeadInRound(p.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.setGameMode(GameMode.SPECTATOR);
            }, 1L);
        }
        // If state == PREP, resetRound will fully restore this player on its scheduled tick —
        // no per-respawn action needed here.
    }

    private void resetRound(DuelMatch m) {
        if (m.state() != MatchState.PREP) return;
        Arena arena = config.arena(m.arenaId());
        if (arena == null || !arena.canFit(m.teamA().size(), m.teamB().size())) {
            // Arena got reconfigured or deleted mid-match; bail out cleanly.
            endMatch(m, 0);
            return;
        }
        List<Location> spawnsA = arena.spawnsA();
        List<Location> spawnsB = arena.spawnsB();
        for (int i = 0; i < m.teamA().size(); i++) {
            Player p = Bukkit.getPlayer(m.teamA().get(i));
            if (p == null) continue;
            prepareFighter(p, m.kitName(), spawnsA.get(i));
        }
        for (int i = 0; i < m.teamB().size(); i++) {
            Player p = Bukkit.getPlayer(m.teamB().get(i));
            if (p == null) continue;
            prepareFighter(p, m.kitName(), spawnsB.get(i));
        }
        m.clearDeadInRound();

        Component prep = MM.deserialize(config.msg("match-prep"));
        for (UUID id : m.allPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(Title.title(prep, Component.text("Next round…"),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
            }
        }
        refreshPlayerVisibility(m.allPlayers());
        scheduleFreeze(m, false);
    }

    private void endMatch(DuelMatch m, int winningTeam) {
        if (m.state() == MatchState.ENDED) return;
        m.setState(MatchState.ENDED);
        cancelTask(m.freezeTask()); m.setFreezeTask(null);
        cancelTask(m.timerTask()); m.setTimerTask(null);
        cancelTask(m.actionBarTask()); m.setActionBarTask(null);

        if (winningTeam == 1 || winningTeam == 2) {
            List<UUID> winners = winningTeam == 1 ? m.teamA() : m.teamB();
            List<UUID> losers = winningTeam == 1 ? m.teamB() : m.teamA();
            int wScore = winningTeam == 1 ? m.roundsA() : m.roundsB();
            int lScore = winningTeam == 1 ? m.roundsB() : m.roundsA();
            broadcastPrefixed(MM.deserialize(config.msg("match-result-broadcast"),
                Placeholder.parsed("winner", joinNames(winners)),
                Placeholder.parsed("loser", joinNames(losers)),
                Placeholder.parsed("wscore", String.valueOf(wScore)),
                Placeholder.parsed("lscore", String.valueOf(lScore))));
            // 1v1 stats only for now — team duel stats can come later.
            if (!m.isTeamMatch()) {
                recordDuelStats(m, winners.get(0), losers.get(0), wScore, lScore);
            }
        }

        for (UUID id : m.allPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean won = winningTeam != 0 && winningTeam == m.teamOf(id);
            finalizePlayer(p, won, m);
        }

        List<UUID> allIds = m.allPlayers();
        for (UUID id : allIds) matchByPlayer.remove(id);
        activeByArena.remove(m.arenaId());

        refreshPlayerVisibility(allIds);

        // Give each finished player a "Requeue: <kit>" paper once they've settled.
        // Ranked matches give a ranked-tagged paper so right-click re-enqueues into ranked.
        if (queues != null) {
            String kit = m.kitName();
            boolean wasRanked = m.ranked();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (UUID id : allIds) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && !isInMatch(id)) queues.giveRequeueItem(p, kit, wasRanked);
                }
            }, 20L);
        }

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

        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

        Location lobby = config.lobby();
        Location savedLoc = m.savedLocation().get(p.getUniqueId());
        Location destination = lobby != null ? lobby : savedLoc;

        // Dead loser path — defer to respawn handling so Bukkit doesn't drop the teleport.
        if (p.isDead()) {
            if (destination != null) postMatchTeleport.put(p.getUniqueId(), destination);
            DuelMatch matchRef = m;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (p.isDead()) p.spigot().respawn();
                restoreSavedLoadout(p, matchRef);
            }, 6L);
            return;
        }

        // Spectators (dead-in-round players from a finished match) need to come back to survival
        // BEFORE the lobby teleport — spectator-mode players can't be teleported reliably.
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }

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

    private void recordDuelStats(DuelMatch m, UUID winnerId, UUID loserId, int wRounds, int lRounds) {
        com.samarth.stats.StatsService stats = com.samarth.duels.stats.StatsBridge.tryGet();
        if (stats == null) return;
        long durationSec = m.startMillisOrZero() == 0
            ? 0
            : Math.max(0, (System.currentTimeMillis() - m.startMillisOrZero()) / 1000L);
        com.samarth.stats.model.DuelResult result = new com.samarth.stats.model.DuelResult(
            System.currentTimeMillis(),
            winnerId, loserId,
            m.kitName(),
            m.firstTo(),
            wRounds, lRounds,
            durationSec,
            m.ranked());
        if (m.ranked()) {
            stats.recordRankedDuelResult(result, (winnerUpdate, loserUpdate) -> {
                Player wp = Bukkit.getPlayer(winnerUpdate.uuid());
                Player lp = Bukkit.getPlayer(loserUpdate.uuid());
                if (wp != null) send(wp, eloDeltaMsg(true, winnerUpdate));
                if (lp != null) send(lp, eloDeltaMsg(false, loserUpdate));
            });
        } else {
            stats.recordDuelResult(result);
        }
    }

    private String eloDeltaMsg(boolean won, com.samarth.stats.model.RankedUpdate u) {
        String sign = u.delta() >= 0 ? "+" : "";
        if (won) {
            return "<gold>Ranked win!</gold> <gray>Elo: <white>" + u.newElo()
                + "</white> <green>(" + sign + u.delta() + ")</green></gray>";
        }
        return "<red>Ranked loss.</red> <gray>Elo: <white>" + u.newElo()
            + "</white> <red>(" + sign + u.delta() + ")</red></gray>";
    }

    /**
     * Forfeit while still online — opposing team wins immediately. In team duels,
     * the forfeiting player's whole team eats the loss (simpler v1 semantic; we
     * can soften this later to "forfeit only the player, team plays short-handed"
     * if there's demand). Returns true if a match was forfeited.
     */
    public boolean forfeitMatch(Player p) {
        DuelMatch m = matchByPlayer.get(p.getUniqueId());
        if (m == null) return false;
        int team = m.teamOf(p.getUniqueId());
        if (team == 0) return false;
        int winningTeam = (team == 1) ? 2 : 1;
        broadcastPrefixed(MM.deserialize("<yellow><player> forfeited the duel.</yellow>",
            Placeholder.parsed("player", p.getName())));
        endMatch(m, winningTeam);
        return true;
    }

    public void handleDisconnect(Player p) {
        DuelMatch m = matchByPlayer.get(p.getUniqueId());
        if (m == null) return;
        int team = m.teamOf(p.getUniqueId());
        if (team == 0) return;

        broadcastPrefixed(MM.deserialize("<gray><player> disconnected.</gray>",
            Placeholder.parsed("player", p.getName())));

        // Treat them as eliminated for round purposes.
        m.markDeadInRound(p.getUniqueId());
        matchByPlayer.remove(p.getUniqueId());

        boolean teamAOut = m.teamAFullyDead();
        boolean teamBOut = m.teamBFullyDead();
        if (teamAOut && !teamBOut) { endMatch(m, 2); return; }
        if (teamBOut && !teamAOut) { endMatch(m, 1); return; }
        if (teamAOut && teamBOut) { endMatch(m, 0); return; }
        // Otherwise: teammates are still alive — match continues without them.
    }

    private void startNextWaiting() {
        if (waiting.isEmpty()) return;
        // Scan waiters and try to schedule each into any free, fitting arena.
        // We don't strictly preserve FIFO when a smaller match fits but a bigger one is at the head —
        // that's a feature (no head-of-line blocking on a too-large match).
        for (int i = 0; i < waiting.size(); i++) {
            PendingDuel pd = waiting.get(i);
            Arena free = firstFreeArena(pd.teamA.size(), pd.teamB.size());
            if (free == null) continue;
            List<Player> teamA = onlinePlayersFor(pd.teamA);
            List<Player> teamB = onlinePlayersFor(pd.teamB);
            waiting.remove(i);
            if (teamA.size() != pd.teamA.size() || teamB.size() != pd.teamB.size()) {
                // Someone logged out — drop this waiter and try the next.
                i--;
                continue;
            }
            start(teamA, teamB, pd.kit, pd.firstTo, pd.useTimeLimit, pd.ranked);
            // start() may itself queue if all arenas filled in the meantime; either way, exit.
            return;
        }
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

    /** Build/refresh the duel sidebar (server IP + live round score) and attach to all fighters. */
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
            try {
                obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
            } catch (Throwable ignored) {
                // Older Paper — score numbers visible; acceptable.
            }
        }
        for (String entry : new ArrayList<>(sb.getEntries())) {
            sb.resetScores(entry);
        }

        applyTeamColors(sb, m);

        int needed = m.roundsNeeded();
        String ip = config.serverIp();
        String teamALabel = m.isTeamMatch()
            ? "Team A (" + m.teamA().size() + ")"
            : nameOf(m.playerA());
        String teamBLabel = m.isTeamMatch()
            ? "Team B (" + m.teamB().size() + ")"
            : nameOf(m.playerB());

        // Score numbers determine vertical order (higher score = top).
        setLine(obj, "§7Server", 7);
        setLine(obj, "§b" + ip, 6);
        setLine(obj, "§r", 5);
        setLine(obj, "§b" + truncate(teamALabel, 16) + " §8: §f" + m.roundsA(), 4);
        setLine(obj, "§c" + truncate(teamBLabel, 16) + " §8: §f" + m.roundsB(), 3);
        setLine(obj, "§l", 2);
        setLine(obj, "§7First to §f" + needed, 1);

        for (UUID id : m.allPlayers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(sb);
        }
    }

    private static void setLine(Objective obj, String entry, int scoreValue) {
        Score s = obj.getScore(entry);
        s.setScore(scoreValue);
    }

    /**
     * Color team-A nameplates aqua, team-B red, and disable friendly collision so
     * teammates can't body-block each other mid-fight. Visible only to players who
     * have this match's scoreboard attached (the fighters themselves).
     */
    private static void applyTeamColors(Scoreboard sb, DuelMatch m) {
        org.bukkit.scoreboard.Team teamA = sb.getTeam("duel_a");
        if (teamA == null) {
            teamA = sb.registerNewTeam("duel_a");
            teamA.color(NamedTextColor.AQUA);
            teamA.setAllowFriendlyFire(false);
            teamA.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE,
                org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS);
        }
        org.bukkit.scoreboard.Team teamB = sb.getTeam("duel_b");
        if (teamB == null) {
            teamB = sb.registerNewTeam("duel_b");
            teamB.color(NamedTextColor.RED);
            teamB.setAllowFriendlyFire(false);
            teamB.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE,
                org.bukkit.scoreboard.Team.OptionStatus.FOR_OTHER_TEAMS);
        }
        for (String entry : new ArrayList<>(teamA.getEntries())) teamA.removeEntry(entry);
        for (String entry : new ArrayList<>(teamB.getEntries())) teamB.removeEntry(entry);
        for (UUID id : m.teamA()) {
            String name = nameOf(id);
            if (!"?".equals(name)) teamA.addEntry(name);
        }
        for (UUID id : m.teamB()) {
            String name = nameOf(id);
            if (!"?".equals(name)) teamB.addEntry(name);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ----- helpers -----

    private void showFightTitle(Player p) {
        Component fight = MM.deserialize(config.msg("match-fight"));
        p.showTitle(Title.title(fight, Component.empty(),
            Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(800), Duration.ofMillis(200))));
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

    private void broadcastTo(List<Player> players, String msg) {
        for (Player p : players) send(p, msg);
    }

    private void broadcastPrefixed(Component c) {
        // Direct iteration instead of Bukkit.broadcast(...) — that gates on the
        // `bukkit.broadcast.user` permission, which isn't reliably granted on
        // perm-plugin setups (LuckPerms wiped defaults, etc.).
        Component out = MM.deserialize(config.prefix()).append(c);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(out);
        Bukkit.getConsoleSender().sendMessage(out);
    }

    private static String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private static String joinNames(List<UUID> ids) {
        if (ids.isEmpty()) return "?";
        if (ids.size() == 1) return nameOf(ids.get(0));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(nameOf(ids.get(i)));
        }
        return sb.toString();
    }

    private static String labelFor(List<Player> team) {
        if (team.size() == 1) return team.get(0).getName();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < team.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(team.get(i).getName());
        }
        return sb.toString();
    }

    private static List<UUID> toIds(List<Player> players) {
        List<UUID> out = new ArrayList<>(players.size());
        for (Player p : players) out.add(p.getUniqueId());
        return out;
    }

    private static List<Player> onlinePlayersFor(List<UUID> ids) {
        List<Player> out = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    private static boolean hasOnlinePlayerInTeam(List<UUID> team) {
        for (UUID id : team) {
            if (Bukkit.getPlayer(id) != null) return true;
        }
        return false;
    }

    private void cancelTask(@Nullable BukkitTask t) {
        if (t != null) try { t.cancel(); } catch (IllegalStateException ignored) {}
    }

    private static final class PendingDuel {
        final List<UUID> teamA;
        final List<UUID> teamB;
        final String kit;
        final int firstTo;
        final boolean useTimeLimit;
        final boolean ranked;
        PendingDuel(List<UUID> teamA, List<UUID> teamB, String kit, int firstTo,
                    boolean useTimeLimit, boolean ranked) {
            this.teamA = List.copyOf(teamA);
            this.teamB = List.copyOf(teamB);
            this.kit = kit;
            this.firstTo = firstTo;
            this.useTimeLimit = useTimeLimit;
            this.ranked = ranked;
        }
    }
}
