package com.ichezzy.evolutionboost.dimension;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Hält die Dimension "event:halloween" permanent in Nacht
 * und verhindert Fortschritt der Tageszeit – ohne Log-Spam.
 */
public final class HalloweenTimeLock {
    private static final ResourceLocation HALLOWEEN_DIM = ResourceLocation.parse("event:halloween");
    // später erweiterbar um weitere Zonen:
    private static final Set<ResourceLocation> TARGETS = Set.of(HALLOWEEN_DIM);

    private static final long NIGHT_TIME = 18000L; // vanilla Nacht

    private HalloweenTimeLock() {}

    public static void init() {
        // Beim Serverstart einmalig alle Zielwelten auf Nacht setzen.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                if (isTarget(level)) {
                    level.setDayTime(NIGHT_TIME);
                }
            }
        });

        // JEDEN Welt-Tick: Nur für unsere Zielwelten Zeit auf 18000 zurücksetzen.
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (!isTarget(level)) return;

            if (level.getDayTime() != NIGHT_TIME) {
                level.setDayTime(NIGHT_TIME);
            }

            // Debug-Ausgabe aus Haupt-Config steuern
            if (isDebug(level.getServer())) {
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

    private static boolean isDebug(MinecraftServer server) {
        // liest aus /config/evolutionboost/main.json
        return EvolutionBoostConfig.get().halloweenDebug;
    }

    private static boolean isTarget(ServerLevel level) {
        return TARGETS.contains(level.dimension().location());
    }
}
