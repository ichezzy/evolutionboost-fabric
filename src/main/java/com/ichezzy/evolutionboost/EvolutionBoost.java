package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.command.RewardCommand;
import com.ichezzy.evolutionboost.compat.cobblemon.HooksRegistrar;
import com.ichezzy.evolutionboost.item.ModItemGroup;
import com.ichezzy.evolutionboost.item.ModItems;
import com.ichezzy.evolutionboost.reward.RewardRoles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
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

        // ---- Items / Creative Tab ----
        ModItems.registerAll();
        ModItemGroup.register();

        // ---- Commands ----
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            com.ichezzy.evolutionboost.command.BoostCommand.register();
            RewardCommand.register(dispatcher);
        });

        // ---- Cobblemon Hooks + Rewards Rollen laden ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BoostManager.get(server);
            RewardRoles.load(server); // Rollen (Donator/Gym) einlesen
            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                HooksRegistrar.register(server);
                LOGGER.info("Cobblemon detected – hooks registered");
            } else {
                LOGGER.warn("Cobblemon not detected – hooks skipped");
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(EvolutionBoost::safeUnregister);

        // ---- Login-Hinweis, falls Rewards offen ----
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.player; // Mojang mappings: Feld ist öffentlich
            if (com.ichezzy.evolutionboost.reward.RewardManager.hasAnyReady(p)) {
                p.sendSystemMessage(Component.literal("§aYou have rewards to claim! Use §e/rewards claim <type>§a or §e/rewards info§a."));
            }
        });

        // ---- Ticker (Boost-Manager) ----
        ServerTickEvents.END_SERVER_TICK.register(server -> BoostManager.get(server).tick(server));
    }

    /** optional – ruft HooksRegistrar.unregister nur auf, wenn vorhanden */
    private static void safeUnregister(MinecraftServer server) {
        try {
            HooksRegistrar.class.getMethod("unregister", MinecraftServer.class).invoke(null, server);
        } catch (Throwable ignored) { }
    }
}
