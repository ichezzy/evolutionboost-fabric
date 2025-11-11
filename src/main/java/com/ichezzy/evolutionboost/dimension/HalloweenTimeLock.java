package com.ichezzy.evolutionboost.dimension;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fixiert NUR "event:halloween" auf Mitternacht & Vollmond. */
public final class HalloweenTimeLock {
    private HalloweenTimeLock() {}

    private static final Logger LOG = LoggerFactory.getLogger("EvolutionBoost/HalloweenTimeLock");
    private static final ResourceLocation HALLOWEEN_DIM_ID =
            ResourceLocation.fromNamespaceAndPath("event", "halloween");

    private static final long TARGET_TIME = 0L * 24000L + 18000L; // Vollmond-Tag 0 + Mitternacht
    private static final int  RESET_EVERY_N_TICKS = 5;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register((ServerLevel world) -> {
            // 1) Hook-Check (nur jede Sekunde loggen, um Spam zu vermeiden)
            if (world.getGameTime() % 20 == 0 && world.dimension().location().equals(HALLOWEEN_DIM_ID)) {
                LOG.info("Tick in {} (OK). dayTime={}, gameTime={}",
                        world.dimension().location(), world.getDayTime(), world.getGameTime());
            }

            // 2) Nur die Ziel-Dimension manipulieren
            if (!world.dimension().location().equals(HALLOWEEN_DIM_ID)) return;
            if (world.getGameTime() % RESET_EVERY_N_TICKS != 0) return;

            // Vollmond + Mitternacht: setze TOTAL dayTime auf ein Vielfaches von 8 Tage + 18000
            long days = world.getDayTime() / 24000L;
            long alignedDay = (days - (days % 8L));     // 8er-Multiplikator
            long absolute = alignedDay * 24000L + 18000L;
            world.setDayTime(absolute);
        });
    }
}
