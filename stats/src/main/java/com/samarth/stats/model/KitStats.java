package com.samarth.stats.model;

public record KitStats(String kit, int wins, int losses) {
    public int total() { return wins + losses; }
    public double winRate() {
        int t = total();
        return t == 0 ? 0.0 : (double) wins / t;
    }
}
