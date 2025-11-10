package com.ichezzy.evolutionboost.dimension;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Fixiert NUR "event:halloween" auf Mitternacht & Vollmond. */
public final class HalloweenTimeLock {
    private HalloweenTimeLock() {}

    private static final ResourceLocation HALLOWEEN_DIM_ID =
            ResourceLocation.fromNamespaceAndPath("event", "halloween");

    private static final long TARGET_TIME = 0L * 24000L + 18000L; // Vollmond-Tag 0 + Mitternacht
    private static final int  RESET_EVERY_N_TICKS = 5;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register((ServerLevel world) -> {
            // prüfe Dimension via ResourceLocation (robust gegen falsch erzeugte Keys)
            if (!world.dimension().location().equals(HALLOWEEN_DIM_ID)) return;
            if (world.getGameTime() % RESET_EVERY_N_TICKS != 0) return;
            world.setDayTime(TARGET_TIME);
        });
    }
}
