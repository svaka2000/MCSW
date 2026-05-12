package com.samarth.stats.model;

import java.util.UUID;

public record LeaderboardEntry(int rank, UUID uuid, String name, double score) {}
