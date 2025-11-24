package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.server.MinecraftServer;

public final class HooksRegistrar {
    private HooksRegistrar() {}

    private static Class<?> COBBLEMON_EVENTS;
    private static Object   PRIORITY_NORMAL;

    public static void register(MinecraftServer server) {
        try {
            COBBLEMON_EVENTS = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            // Priority enum (falls vorhanden), sonst null
            try {
                Class<?> prio = Class.forName("com.cobblemon.mod.common.api.events.Priority");
                PRIORITY_NORMAL = prio.getEnumConstants()[0]; // meist "NORMAL"
            } catch (ClassNotFoundException ignore) { PRIORITY_NORMAL = null; }

            // XP
            XpHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);
            // SHINY
            ShinyHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);
            // DROP
            DropHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);
            // IV
            IvHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);

            EvolutionBoost.LOGGER.info("[compat] Cobblemon hooks registered (XP/SHINY/DROP/IV).");
        } catch (Throwable t) {
            EvolutionBoost.LOGGER.warn("[compat] Cobblemon not present or API changed: {}", t.toString());
        }
    }

    public static void unregister(MinecraftServer server) {
        // optional: Unsubscribe, wenn eure ReflectUtils das unterstützt – derzeit no-op.
    }
}
