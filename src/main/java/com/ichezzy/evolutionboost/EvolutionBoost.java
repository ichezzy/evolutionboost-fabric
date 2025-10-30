package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.compat.cobblemon.HooksRegistrar;
import com.ichezzy.evolutionboost.item.ModItemGroup;
import com.ichezzy.evolutionboost.item.ModItems;
import com.ichezzy.evolutionboost.command.RewardCommand;
import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.world.DimensionTimeHook;
import com.ichezzy.evolutionboost.world.HalloweenWeatherHook;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
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

        // ---- Rewards: init + login messages ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RewardManager.init(server);
            BoostManager.get(server);

            // Cobblemon Hooks
            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                HooksRegistrar.register(server);
                LOGGER.info("Cobblemon detected – hooks registered");
            } else {
                LOGGER.warn("Cobblemon not detected – hooks skipped");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(EvolutionBoost::safeUnregister);

        // Login-Hinweis bei Join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler != null && handler.player != null) {
                RewardManager.onPlayerJoin(handler.player);
            }
        });

        // ---- Commands ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            RewardCommand.register(dispatcher);
            com.ichezzy.evolutionboost.command.BoostCommand.register();
        });

        // ---- Dimension-spezifische Hooks (nur event:halloween) ----
        DimensionTimeHook.init();      // Permanente Vollmond-Mitternacht
        HalloweenWeatherHook.init();   // 25m calm / 5m thunder + Chat-Nachrichten

        // ---- Tick ----
        ServerTickEvents.END_SERVER_TICK.register(server -> BoostManager.get(server).tick(server));
    }

    /** optional – ruft HooksRegistrar.unregister nur auf, wenn vorhanden */
    private static void safeUnregister(MinecraftServer server) {
        try {
            HooksRegistrar.class.getMethod("unregister", MinecraftServer.class).invoke(null, server);
        } catch (Throwable ignored) { }
        RewardManager.saveAll();
    }
}
