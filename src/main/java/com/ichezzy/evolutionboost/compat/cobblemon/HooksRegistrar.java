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
                Class<?> prio = Class.forName("com.cobblemon.mod.common.api.Priority");
                PRIORITY_NORMAL = prio.getEnumConstants()[0]; // meist "NORMAL"
            } catch (ClassNotFoundException ignore) { PRIORITY_NORMAL = null; }

            // XP
            XpHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);
            // SHINY
            ShinyHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);
            // IV
            IvHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);

            // EV - nur wenn Event verfügbar (Cobblemon 1.7+)
            try {
                // Prüfen ob das Event existiert
                COBBLEMON_EVENTS.getField("EV_GAINED_EVENT_PRE");
                EvHook.register(server, COBBLEMON_EVENTS, PRIORITY_NORMAL);
                EvolutionBoost.LOGGER.info("[compat] EV hook registered (Cobblemon 1.7+).");
            } catch (NoSuchFieldException e) {
                EvolutionBoost.LOGGER.info("[compat] EV hook skipped (requires Cobblemon 1.7+).");
            }

            EvolutionBoost.LOGGER.info("[compat] Cobblemon hooks registered (XP/SHINY/IV, EV if available).");
        } catch (Throwable t) {
            EvolutionBoost.LOGGER.warn("[compat] Cobblemon not present or API changed: {}", t.toString());
        }
    }

    public static void unregister(MinecraftServer server) {
        // optional: Unsubscribe – derzeit no-op.
    }
}