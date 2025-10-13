package com.ichezzy.evolutionboost.boost;

import java.util.UUID;

public class ActiveBoost {
    public final BoostType type;
    public final BoostScope scope;
    public final double multiplier;
    public final long startTimeMs;
    public final long durationMs;
    public final long endTimeMs;
    public final UUID player;       // null for GLOBAL
    public final String playerName; // optional, zur Anzeige

    // runtime-only
    public String bossBarId;

    public ActiveBoost(BoostType type, BoostScope scope, double multiplier, long durationMs, UUID player) {
        this(type, scope, multiplier, durationMs, player, null);
    }

    public ActiveBoost(BoostType type, BoostScope scope, double multiplier, long durationMs, UUID player, String playerName) {
        this.type = type;
        this.scope = scope;
        this.multiplier = multiplier;
        this.startTimeMs = System.currentTimeMillis();
        this.durationMs = Math.max(1000L, durationMs);
        this.endTimeMs = this.startTimeMs + this.durationMs;
        this.player = player;
        this.playerName = playerName;
    }

    public long millisLeft(long now) { return Math.max(0, endTimeMs - now); }
}
