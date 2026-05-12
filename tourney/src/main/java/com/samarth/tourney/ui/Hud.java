package com.samarth.tourney.ui;

import com.samarth.tourney.config.TourneyConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public final class Hud {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final TourneyConfig config;
    @Nullable private BossBar joinBar;
    private final Map<UUID, BossBar> shownJoinBars = new HashMap<>();

    public Hud(TourneyConfig config) {
        this.config = config;
    }

    public void showJoinBarToAll(long remainingSeconds, int joinedCount) {
        if (joinBar == null) {
            joinBar = BossBar.bossBar(
                renderJoinTitle(remainingSeconds, joinedCount),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
            );
        }
        joinBar.name(renderJoinTitle(remainingSeconds, joinedCount));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!shownJoinBars.containsKey(p.getUniqueId())) {
                p.showBossBar(joinBar);
                shownJoinBars.put(p.getUniqueId(), joinBar);
            }
        }
    }

    public void updateJoinBar(long totalSeconds, long remainingSeconds, int joinedCount) {
        if (joinBar == null) return;
        float progress = totalSeconds <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) remainingSeconds / (float) totalSeconds));
        joinBar.progress(progress);
        joinBar.name(renderJoinTitle(remainingSeconds, joinedCount));
    }

    public void hideJoinBar() {
        if (joinBar == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(joinBar);
        }
        shownJoinBars.clear();
        joinBar = null;
    }

    public void showPlayerOnJoinBar(Player p) {
        if (joinBar == null) return;
        if (!shownJoinBars.containsKey(p.getUniqueId())) {
            p.showBossBar(joinBar);
            shownJoinBars.put(p.getUniqueId(), joinBar);
        }
    }

    private Component renderJoinTitle(long remainingSeconds, int joinedCount) {
        return MM.deserialize(config.msg("countdown-bossbar"),
            Placeholder.parsed("time", formatMmSs(remainingSeconds)),
            Placeholder.parsed("count", String.valueOf(joinedCount)));
    }

    public void prepTitle(Player p, String opponent) {
        Component title = MM.deserialize(config.msg("match-prep-title"));
        Component sub = MM.deserialize(config.msg("match-prep-subtitle"),
            Placeholder.parsed("opponent", opponent));
        p.showTitle(Title.title(title, sub,
            Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(200))));
    }

    public void countdownTitle(Player p, int seconds) {
        Component title = Component.text(String.valueOf(seconds));
        Component sub = MM.deserialize("<gray>Get ready</gray>");
        p.showTitle(Title.title(title, sub,
            Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(700), Duration.ofMillis(150))));
    }

    public void fightTitle(Player p) {
        Component title = MM.deserialize(config.msg("match-fight-title"));
        p.showTitle(Title.title(title, Component.empty(),
            Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(800), Duration.ofMillis(200))));
    }

    public void winTitle(Player p) {
        Component title = MM.deserialize(config.msg("match-win-title"));
        p.showTitle(Title.title(title, Component.empty(),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))));
    }

    public void lossTitle(Player p) {
        Component title = MM.deserialize(config.msg("match-loss-title"));
        p.showTitle(Title.title(title, Component.empty(),
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(300))));
    }

    public void killScore(Player p, int you, int opp) {
        Component bar = MM.deserialize(config.msg("kill-actionbar"),
            Placeholder.parsed("you", String.valueOf(you)),
            Placeholder.parsed("opp", String.valueOf(opp)));
        p.sendActionBar(bar);
    }

    public static String formatMmSs(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public static MiniMessage mm() { return MM; }
}
