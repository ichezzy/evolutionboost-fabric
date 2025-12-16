package com.ichezzy.evolutionboost.boost;

public class ActiveBoost {
    public final BoostType type;
    public final BoostScope scope;
    public double multiplier;
    public final long startTimeMs;
    public final long durationMs;
    public final long endTimeMs;

    // runtime-only (Key im BoostManager-Bossbar-Map)
    public String bossBarId;

    public ActiveBoost(BoostType type, BoostScope scope, double multiplier, long durationMs) {
        this.type = type;
        this.scope = scope;
        this.multiplier = multiplier;
        this.startTimeMs = System.currentTimeMillis();
        this.durationMs = Math.max(1000L, durationMs);
        this.endTimeMs = this.startTimeMs + this.durationMs;
    }

    public long millisLeft(long now) {
        return Math.max(0L, endTimeMs - now);
    }
}
