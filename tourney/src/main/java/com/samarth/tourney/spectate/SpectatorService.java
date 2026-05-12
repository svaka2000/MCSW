package com.samarth.tourney.spectate;

import com.samarth.tourney.config.TourneyConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class SpectatorService {
    private final TourneyConfig config;
    private final Map<UUID, GameMode> savedModes = new HashMap<>();
    private final Map<UUID, Location> savedLocations = new HashMap<>();
    private final Map<UUID, UUID> spectatorTarget = new HashMap<>();
    private final Set<UUID> spectators = new HashSet<>();

    public SpectatorService(TourneyConfig config) {
        this.config = config;
    }

    public void enter(Player p, Player target) {
        if (!spectators.contains(p.getUniqueId())) {
            savedModes.put(p.getUniqueId(), p.getGameMode());
            savedLocations.put(p.getUniqueId(), p.getLocation());
            spectators.add(p.getUniqueId());
        }
        spectatorTarget.put(p.getUniqueId(), target.getUniqueId());
        p.setGameMode(GameMode.SPECTATOR);
        p.setSpectatorTarget(target);
    }

    public void exit(Player p) {
        spectatorTarget.remove(p.getUniqueId());
        if (!spectators.remove(p.getUniqueId())) return;
        GameMode mode = savedModes.remove(p.getUniqueId());
        Location loc = savedLocations.remove(p.getUniqueId());
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setSpectatorTarget(null);
            p.setGameMode(mode != null ? mode : GameMode.SURVIVAL);
        }
        // Reset to server-wide main scoreboard so the spectator sidebar disappears.
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        Location lobby = config.lobby();
        if (lobby != null) {
            p.teleport(lobby);
        } else if (loc != null) {
            p.teleport(loc);
        }
    }

    public boolean isSpectator(UUID id) {
        return spectators.contains(id);
    }

    public @Nullable UUID targetOf(UUID spectatorId) {
        return spectatorTarget.get(spectatorId);
    }

    public Set<UUID> spectators() {
        return spectators;
    }
}
