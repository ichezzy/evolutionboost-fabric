package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.command.EventTpCommand;
import com.ichezzy.evolutionboost.command.HalloweenCommand;
import com.ichezzy.evolutionboost.command.RewardCommand;
import com.ichezzy.evolutionboost.compat.cobblemon.HooksRegistrar;
import com.ichezzy.evolutionboost.item.ModItemGroup;
import com.ichezzy.evolutionboost.item.ModItems;
import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.ticket.TicketManager;
import com.ichezzy.evolutionboost.dimension.DimensionTimeHook;
import com.ichezzy.evolutionboost.dimension.HalloweenWeatherHook;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvolutionBoost implements ModInitializer {
    public static final String MOD_ID = "evolutionboost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Initializing…", MOD_ID);

        // Items / Creative Tab
        ModItems.registerAll();
        ModItemGroup.register();

        // Server start
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RewardManager.init(server);
            BoostManager.get(server);

            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                HooksRegistrar.register(server);
                LOGGER.info("Cobblemon detected – hooks registered");
            } else {
                LOGGER.warn("Cobblemon not detected – hooks skipped");
            }

            HalloweenWeatherHook.init(server);
            DimensionTimeHook.init(server);
            TicketManager.init(server);
        });

        // persist / cleanup
        ServerLifecycleEvents.SERVER_STOPPING.register(EvolutionBoost::safeUnregister);

        // login hint
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler != null ? handler.player : null;
            if (player != null) RewardManager.onPlayerJoin(player);
        });

        // Commands
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            CommandDispatcher<CommandSourceStack> d = server.getCommands().getDispatcher();
            RewardCommand.register(d);
            com.ichezzy.evolutionboost.command.BoostCommand.register();
            EventTpCommand.register(d);
            HalloweenCommand.register(d);

            // zentrale Wrapper unter /evolutionboost
            d.register(Commands.literal("evolutionboost")
                    .then(Commands.literal("halloween").redirect(d.getRoot().getChild("halloween")))
                    .then(Commands.literal("event").redirect(d.getRoot().getChild("event")))       // alias für EventTpCommand
                    .then(Commands.literal("rewards").redirect(d.getRoot().getChild("rewards")))
            );
        });

        // tick
        ServerTickEvents.END_SERVER_TICK.register(server -> BoostManager.get(server).tick(server));
    }

    private static void safeUnregister(MinecraftServer server) {
        try {
            HooksRegistrar.class.getMethod("unregister", MinecraftServer.class).invoke(null, server);
        } catch (Throwable ignored) {}
        RewardManager.saveAll();
    }
}
