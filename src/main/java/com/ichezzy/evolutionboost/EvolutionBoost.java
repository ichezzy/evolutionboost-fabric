package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.compat.cobblemon.HooksRegistrar;
import com.ichezzy.evolutionboost.item.ModItemGroup;
import com.ichezzy.evolutionboost.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvolutionBoost implements ModInitializer {
    public static final String MOD_ID = "evolutionboost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Initializing…", MOD_ID);

        // ---- Items / Creative Tab ----
        ModItems.registerAll();
        ModItemGroup.register();

        // ---- Cobblemon Hooks ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BoostManager.get(server);
            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                HooksRegistrar.register(server);
                LOGGER.info("Cobblemon detected – hooks registered");
            } else {
                LOGGER.warn("Cobblemon not detected – hooks skipped");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(EvolutionBoost::safeUnregister);

        // ---- Ticker / Commands ----
        ServerTickEvents.END_SERVER_TICK.register(server -> BoostManager.get(server).tick(server));
        com.ichezzy.evolutionboost.command.BoostCommand.register();
    }

    /** optional – ruft HooksRegistrar.unregister nur auf, wenn vorhanden */
    private static void safeUnregister(MinecraftServer server) {
        try {
            HooksRegistrar.class.getMethod("unregister", MinecraftServer.class).invoke(null, server);
        } catch (Throwable ignored) { }
    }
}
