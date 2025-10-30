package com.ichezzy.evolutionboost.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Erzwingt in der Dimension "event:halloween" permanente Vollmond-Mitternacht.
 * Hinweis: Wir setzen absichtlich jeden Tick eine feste DayTime, sodass die
 * Mondphase konstant bleibt (Tag % 8 == 0, z.B. Tag 0) und es visuell immer Vollmond ist.
 */
public final class DimensionTimeHook {
    private DimensionTimeHook() {}

    // world/dimensions/event/halloween
    public static final ResourceKey<Level> HALLOWEEN_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("event", "halloween"));

    private static final long FULLMOON_NIGHT_TICK = 18000L; // Mitternacht
    private static final long FULLMOON_DAY       = 0L;      // Tag 0 => Tag % 8 == 0  => Vollmond
    private static final long FORCED_TIME        = FULLMOON_DAY * 24000L + FULLMOON_NIGHT_TICK;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel halloween = server.getLevel(HALLOWEEN_DIM);
            if (halloween != null) {
                // konstante Vollmond-Nacht erzwingen
                halloween.setDayTime(FORCED_TIME);
            }
        });
    }
}
