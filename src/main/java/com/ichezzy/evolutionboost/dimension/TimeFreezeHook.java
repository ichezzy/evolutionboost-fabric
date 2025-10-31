package com.ichezzy.evolutionboost.dimension;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Fixiert in event:halloween Vollmond + Mitternacht (ohne Gamerule, nur diese Dimension). */
public final class TimeFreezeHook {
    private TimeFreezeHook() {}

    private static final ResourceKey<Level> HALLOWEEN_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:halloween"));

    private static volatile boolean enabled = false;

    public static void init(MinecraftServer server) {
        // standardmäßig aus; per Command einschalten
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (!enabled) return;
            ServerLevel level = s.getLevel(HALLOWEEN_DIM);
            if (level == null) return;

            // Ziel: (Tag % 8 == 0) + Mitternacht
            long current = level.getDayTime();
            long day = current / 24000L;                 // absoluter Tag
            long baseDay = (day / 8L) * 8L;              // Vielfaches von 8 → Vollmond
            long target = baseDay * 24000L + 18000L;     // Mitternacht

            if (current != target) level.setDayTime(target);
        });
    }

    public static void setEnabled(boolean on) { enabled = on; }
    public static boolean isEnabled() { return enabled; }
}
