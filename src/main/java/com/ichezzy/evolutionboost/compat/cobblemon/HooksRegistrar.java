package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class HooksRegistrar {
    private HooksRegistrar() {}

    public static void register(MinecraftServer server) {
        try {
            Class<?> clsEvents   = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Class<?> clsPriority = Class.forName("com.cobblemon.mod.common.api.Priority");
            Object PRIORITY_NORMAL = Enum.valueOf((Class<Enum>) clsPriority.asSubclass(Enum.class), "NORMAL");

            // nur einmal: Liste der Felder loggen
            String names = Arrays.stream(clsEvents.getFields()).map(Field::getName)
                    .sorted().collect(Collectors.joining(", "));
            EvolutionBoost.LOGGER.info("[evolutionboost] CobblemonEvents fields: {}", names);

            ShinyHook.register(server, clsEvents, PRIORITY_NORMAL);
            XpHook.register(server, clsEvents, PRIORITY_NORMAL);
            IvHook.register(server, clsEvents, PRIORITY_NORMAL);
            DropHook.register(server, clsEvents, PRIORITY_NORMAL);

            EvolutionBoost.LOGGER.info("[evolutionboost] hooks registered.");
        } catch (Throwable t) {
            EvolutionBoost.LOGGER.error("[evolutionboost] Failed to register hooks", t);
        }
    }
}
