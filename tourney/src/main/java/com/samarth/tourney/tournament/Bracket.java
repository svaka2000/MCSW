package com.samarth.tourney.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Single-elimination bracket. Pads to next power of 2 with byes.
 *
 * Byes are distributed so the first `byeCount` round-0 matches each have exactly
 * one bye (and the remaining matches are real). This avoids the previous bug
 * where odd player counts produced null-vs-null matches that stalled or
 * accidentally auto-crowned a player in higher rounds.
 */
public final class Bracket {
    private final List<List<BracketMatch>> rounds;

    private Bracket(List<List<BracketMatch>> rounds) {
        this.rounds = rounds;
    }

    public List<List<BracketMatch>> rounds() { return rounds; }

    public int totalRounds() { return rounds.size(); }

    public static Bracket build(List<UUID> players, Random rng) {
        if (players.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 players");
        }
        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, rng);

        int realCount = shuffled.size();
        int slots = 1;
        while (slots < realCount) slots *= 2;
        int matchCount = slots / 2;
        int byeCount = slots - realCount; // 0 if realCount is already a power of 2

        // Distribute byes evenly: first `byeCount` round-0 matches each have one bye.
        // Remaining matches use two real players. byeCount <= matchCount always.
        int totalRounds = Integer.numberOfTrailingZeros(slots);
        List<List<BracketMatch>> rounds = new ArrayList<>(totalRounds);

        List<BracketMatch> first = new ArrayList<>(matchCount);
        int playerIdx = 0;
        for (int i = 0; i < matchCount; i++) {
            BracketMatch m;
            if (i < byeCount) {
                // Bye match: one slot null, the other is a real player.
                UUID p = shuffled.get(playerIdx++);
                m = new BracketMatch(0, i, null, p);
            } else {
                UUID a = shuffled.get(playerIdx++);
                UUID b = shuffled.get(playerIdx++);
                m = new BracketMatch(0, i, a, b);
            }
            first.add(m);
        }
        rounds.add(first);

        for (int r = 1; r < totalRounds; r++) {
            int matches = slots / (1 << (r + 1));
            List<BracketMatch> round = new ArrayList<>(matches);
            for (int i = 0; i < matches; i++) {
                round.add(new BracketMatch(r, i, null, null));
            }
            rounds.add(round);
        }

        Bracket b = new Bracket(rounds);
        b.propagate();
        return b;
    }

    /**
     * Resolve byes (round 0 only) and propagate played-match winners to the next round.
     *
     * Critically, only round-0 matches can be auto-resolved as byes. A later-round
     * match with one null slot is NOT a bye — it's waiting for its feeder match in
     * the previous round to complete. Treating it as a bye was the source of the
     * "winner chosen for no reason" bug with odd player counts.
     */
    public void propagate() {
        // Step 1: resolve round-0 byes (idempotent — only acts on unresolved bye matches)
        if (!rounds.isEmpty()) {
            for (BracketMatch m : rounds.get(0)) {
                if (!m.played() && m.isBye()) {
                    UUID solo = m.playerA() != null ? m.playerA() : m.playerB();
                    m.setWinner(solo);
                    m.setPlayed(true);
                }
            }
        }
        // Step 2: walk all rounds and forward winners to their next-round slots.
        for (int r = 0; r < rounds.size() - 1; r++) {
            for (BracketMatch m : rounds.get(r)) {
                if (!m.played() || m.winner() == null) continue;
                BracketMatch next = rounds.get(r + 1).get(m.index() / 2);
                if (m.index() % 2 == 0) {
                    if (next.playerA() == null) next.setPlayerA(m.winner());
                } else {
                    if (next.playerB() == null) next.setPlayerB(m.winner());
                }
            }
        }
    }

    public @Nullable BracketMatch nextReadyMatch() {
        for (List<BracketMatch> round : rounds) {
            for (BracketMatch m : round) {
                if (m.readyToPlay()) return m;
            }
        }
        return null;
    }

    public boolean isComplete() {
        BracketMatch finalMatch = rounds.get(rounds.size() - 1).get(0);
        return finalMatch.played();
    }

    public @Nullable UUID champion() {
        BracketMatch finalMatch = rounds.get(rounds.size() - 1).get(0);
        return finalMatch.played() ? finalMatch.winner() : null;
    }

    public List<BracketMatch> activelyPlayable() {
        List<BracketMatch> out = new ArrayList<>();
        for (List<BracketMatch> round : rounds) {
            for (BracketMatch m : round) {
                if (m.readyToPlay()) out.add(m);
            }
        }
        return out;
    }
}
