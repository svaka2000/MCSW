package com.samarth.tourney.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Single-elimination bracket. Pads to next power of 2 with byes. */
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

        int slots = 1;
        while (slots < shuffled.size()) slots *= 2;

        // Pad with nulls (byes) at the end of the slot list.
        // For better seed distribution we'd interleave, but uniform random shuffle
        // already removes bias; tail-padding keeps the algorithm trivial.
        List<UUID> padded = new ArrayList<>(shuffled);
        while (padded.size() < slots) padded.add(null);

        int totalRounds = Integer.numberOfTrailingZeros(slots);
        List<List<BracketMatch>> rounds = new ArrayList<>(totalRounds);

        // Round 0
        List<BracketMatch> first = new ArrayList<>();
        for (int i = 0; i < slots / 2; i++) {
            UUID a = padded.get(i * 2);
            UUID b = padded.get(i * 2 + 1);
            first.add(new BracketMatch(0, i, a, b));
        }
        rounds.add(first);

        // Subsequent rounds (empty slots, filled as players advance)
        for (int r = 1; r < totalRounds; r++) {
            int matches = slots / (1 << (r + 1));
            List<BracketMatch> round = new ArrayList<>();
            for (int i = 0; i < matches; i++) {
                round.add(new BracketMatch(r, i, null, null));
            }
            rounds.add(round);
        }

        Bracket b = new Bracket(rounds);
        b.propagate();
        return b;
    }

    /** Resolve byes and propagate winners forward. Idempotent. */
    public void propagate() {
        for (int r = 0; r < rounds.size(); r++) {
            for (BracketMatch m : rounds.get(r)) {
                if (!m.played() && m.isBye()) {
                    UUID solo = m.playerA() != null ? m.playerA() : m.playerB();
                    m.setWinner(solo);
                    m.setPlayed(true);
                }
                if (m.played() && m.winner() != null && r + 1 < rounds.size()) {
                    BracketMatch next = rounds.get(r + 1).get(m.index() / 2);
                    if (m.index() % 2 == 0) {
                        if (next.playerA() == null) next.setPlayerA(m.winner());
                    } else {
                        if (next.playerB() == null) next.setPlayerB(m.winner());
                    }
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
