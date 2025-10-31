package com.ichezzy.evolutionboost.dimension;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Fixiert NUR in "event:halloween" die Zeit auf eine Ziel-Tickzahl.
 * Default-Ziel: Vollmond + Mitternacht (18000; day%8==0).
 * Unabhängig von Gamerules; Overworld bleibt unberührt.
 */
public final class DimensionTimeHook {
    private DimensionTimeHook() {}

    private static final ResourceKey<Level> HALLOWEEN_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:halloween"));

    private static volatile boolean enabled = false;
    private static volatile long targetTick = -1L; // -1 = Vollmond+Mitternacht automatisch
    private static long lastApplied = Long.MIN_VALUE;

    public static void init(MinecraftServer server) {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (!enabled) return;
            ServerLevel level = s.getLevel(HALLOWEEN_DIM);
            if (level == null) return;

            long target = calcTarget(level);
            long cur = level.getDayTime();

            // Re-assert: wenn irgendwas Zeit verstellt, nageln wir sie wieder auf target
            if (cur != target || lastApplied != target) {
                level.setDayTime(target);
                lastApplied = target;
            }
        });
    }

    private static long calcTarget(ServerLevel lvl) {
        if (targetTick >= 0) return targetTick;

        // Vollmond (Tag%8==0) + Mitternacht (18000)
        long cur = lvl.getDayTime();
        long day = cur / 24000L;
        long base = (day / 8L) * 8L; // Vielfaches von 8
        return base * 24000L + 18000L;
    }

    // ===== API (Commands) =====
    public static void setEnabled(boolean on) { enabled = on; }
    public static boolean isEnabled() { return enabled; }
    public static long target() { return targetTick; }
    public static void setMidnight() { targetTick = 18000L; lastApplied = Long.MIN_VALUE; }
    public static void setNoon() { targetTick = 6000L; lastApplied = Long.MIN_VALUE; }
    public static void setTicks(long t) { targetTick = Math.max(0, Math.min(23999, t)); lastApplied = Long.MIN_VALUE; }
}
