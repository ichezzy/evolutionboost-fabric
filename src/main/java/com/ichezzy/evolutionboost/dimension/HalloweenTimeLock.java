package com.ichezzy.evolutionboost.dimension;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.HalloweenConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Hält die Dimension "event:halloween" permanent in Nacht
 * und verhindert Fortschritt der Tageszeit – ohne Log-Spam.
 * Debug-Schalter per Config (config/evolutionboost/halloween.json), default: false.
 */
public final class HalloweenTimeLock {
    // Achtung: Mojang 1.21.x – ctor ist privat. Korrekt ist:
    private static final ResourceLocation HALLOWEEN_DIM = ResourceLocation.fromNamespaceAndPath("event", "halloween");
    private static final Set<ResourceLocation> TARGETS = Set.of(HALLOWEEN_DIM);

    private static final long NIGHT_TIME = 18000L; // Vanilla-Nacht

    private HalloweenTimeLock() {}

    public static void init() {
        // Config laden/erstellen (debug default false)
        HalloweenConfig.loadOrCreate();

        // Beim Serverstart einmalig Nacht setzen
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (isTarget(level)) {
                    level.setDayTime(NIGHT_TIME);
                }
            }
        });

        // JEDEN Welt-Tick: Nur für Zielwelten Zeit auf 18000 zurücksetzen (Freeze)
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (!isTarget(level)) return;

            if (level.getDayTime() != NIGHT_TIME) {
                level.setDayTime(NIGHT_TIME);
            }

            // Dezent debuggen: alle ~10s (200 Ticks), nur wenn in Config aktiviert
            if (HalloweenConfig.get().debug) {
                MinecraftServer server = level.getServer();
                if (server != null && server.getTickCount() % 200 == 0) {
                    EvolutionBoost.LOGGER.info(
                            "[{}] Halloween freeze OK in {} (dayTime={}, gameTime={})",
                            EvolutionBoost.MOD_ID,
                            level.dimension().location(),
                            level.getDayTime(),
                            level.getGameTime()
                    );
                }
            }
        });
    }

    private static boolean isTarget(ServerLevel level) {
        return TARGETS.contains(level.dimension().location());
    }
}
