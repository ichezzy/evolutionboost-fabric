package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.block.ModBlocks;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.command.BoostCommand;
import com.ichezzy.evolutionboost.command.EventCommand;
import com.ichezzy.evolutionboost.command.HelpCommand;
import com.ichezzy.evolutionboost.command.QuestCommand;
import com.ichezzy.evolutionboost.command.RewardCommand;
import com.ichezzy.evolutionboost.command.WeatherCommand;
import com.ichezzy.evolutionboost.compat.cobblemon.HooksRegistrar;
import com.ichezzy.evolutionboost.configs.CommandLogConfig;
import com.ichezzy.evolutionboost.hud.BoostHudSync;
import com.ichezzy.evolutionboost.hud.DimBoostHudPayload;
import com.ichezzy.evolutionboost.item.ModItemGroup;
import com.ichezzy.evolutionboost.item.ModItems;
import com.ichezzy.evolutionboost.item.TicketManager;
import com.ichezzy.evolutionboost.logging.CommandLogManager;
import com.ichezzy.evolutionboost.quest.QuestManager;
import com.ichezzy.evolutionboost.quest.hooks.QuestBattleHook;
import com.ichezzy.evolutionboost.quest.hooks.QuestCatchHook;
import com.ichezzy.evolutionboost.quest.hooks.QuestItemHook;
import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.weather.ChristmasWeatherManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class EvolutionBoost implements ModInitializer {
    public static final String MOD_ID = "evolutionboost";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Initializing…", MOD_ID);

        // ---- Netzwerk-Payloads registrieren ----
        // S2C: Dim-Boost-HUD (schickt pro BoostType den Dimensional-Multiplikator)
        PayloadTypeRegistry.playS2C().register(DimBoostHudPayload.TYPE, DimBoostHudPayload.CODEC);

        // ---- Blocks & Items & Creative Tab ----
        ModBlocks.registerAll();
        ModItems.registerAll();
        ModItemGroup.register();

        // ---- Dim-HUD Sync (nur Dimension-Multiplikatoren) ----
        BoostHudSync.init();

        // ---- Event-Wetter (Christmas-Storm etc.) ----
        ChristmasWeatherManager.init();

        // ---- Commands zentral registrieren (übersteht /reload) ----
        CommandRegistrationCallback.EVENT.register(
                (CommandDispatcher<CommandSourceStack> d,
                 CommandBuildContext registryAccess,
                 net.minecraft.commands.Commands.CommandSelection env) -> {

                    // Jeder Command registriert sich unter /evolutionboost UND /eb
                    HelpCommand.register(d);
                    RewardCommand.register(d);
                    BoostCommand.register(d);
                    EventCommand.register(d);
                    WeatherCommand.register(d);
                    QuestCommand.register(d);
                }
        );

        // ---- Logging früh aktivieren ----
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CommandLogConfig cfg = CommandLogConfig.loadOrCreate();
            CommandLogManager.init(cfg);
            LOGGER.info("[{}] Command logging initialized", MOD_ID);
        });

        // ---- Server gestartet ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            RewardManager.init(server);
            BoostManager.get(server); // init/load
            QuestManager.get().init(server); // Quest-System initialisieren

            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                HooksRegistrar.register(server);
                // Quest-Hooks registrieren (benötigen Cobblemon)
                QuestBattleHook.register();
                QuestCatchHook.register();
                QuestItemHook.register();
                LOGGER.info("Cobblemon detected – hooks registered (incl. Quest hooks)");
            } else {
                LOGGER.warn("Cobblemon not detected – hooks skipped");
            }

            TicketManager.init(server);
        });

        // ---- Stop/Cleanup ----
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            CommandLogManager.close();
            safeUnregister(server);
        });

        // ---- Join-Hinweise ----
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler != null ? handler.player : null;
            if (player != null) {
                RewardManager.onPlayerJoin(player);
                
                // Quest-Benachrichtigung mit 2 Sekunden Verzögerung
                final UUID playerId = player.getUUID();
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000); // 2 Sekunden warten
                        server.execute(() -> {
                            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
                            if (onlinePlayer != null) {
                                QuestManager.get().notifyAvailableQuests(onlinePlayer);
                            }
                        });
                    } catch (InterruptedException ignored) {}
                });
            }
        });

        // ---- Fallschaden in event:christmas deaktivieren ----
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Prüfe ob es Fallschaden ist
            if (source.is(DamageTypes.FALL)) {
                // Prüfe ob Entity in der Christmas-Dimension ist
                ResourceLocation dimId = entity.level().dimension().location();
                if (dimId.getNamespace().equals("event") && dimId.getPath().equals("christmas")) {
                    return false; // Schaden verhindern
                }
            }
            return true; // Schaden erlauben
        });

        // ---- Tick ----
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BoostManager.get(server).tick(server);
            
            // Quest Item Check alle 2 Sekunden (40 Ticks)
            if (server.getTickCount() % 40 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    QuestItemHook.checkInventoryForQuests(player);
                }
            }
        });
    }

    private static void safeUnregister(MinecraftServer server) {
        try {
            HooksRegistrar.class
                    .getMethod("unregister", MinecraftServer.class)
                    .invoke(null, server);
        } catch (Throwable ignored) {
        }
        RewardManager.saveAll();
        QuestManager.get().shutdown(); // Quest-Daten speichern
    }
}
