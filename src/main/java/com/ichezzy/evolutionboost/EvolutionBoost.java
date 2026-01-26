package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.block.ModBlocks;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.command.AdminCommand;
import com.ichezzy.evolutionboost.command.BoostCommand;
import com.ichezzy.evolutionboost.command.DexCommand;
import com.ichezzy.evolutionboost.command.HelpCommand;
import com.ichezzy.evolutionboost.command.HudCommand;
import com.ichezzy.evolutionboost.command.NotificationCommand;
import com.ichezzy.evolutionboost.command.QuestCommand;
import com.ichezzy.evolutionboost.command.RewardCommand;
import com.ichezzy.evolutionboost.command.WeatherCommand;
import com.ichezzy.evolutionboost.compat.cobblemon.HooksRegistrar;
import com.ichezzy.evolutionboost.configs.NotificationConfig;
import com.ichezzy.evolutionboost.dex.DexCatchHook;
import com.ichezzy.evolutionboost.dex.DexDataManager;
import com.ichezzy.evolutionboost.command.GymCommand;
import com.ichezzy.evolutionboost.gym.GymManager;
import com.ichezzy.evolutionboost.hud.BoostHudSync;
import com.ichezzy.evolutionboost.hud.DimBoostHudPayload;
import com.ichezzy.evolutionboost.hud.HudTogglePayload;
import com.ichezzy.evolutionboost.item.ModItemGroup;
import com.ichezzy.evolutionboost.item.ModItems;
import com.ichezzy.evolutionboost.item.TicketManager;
import com.ichezzy.evolutionboost.logging.CommandLogManager;
import com.ichezzy.evolutionboost.permission.PermissionRegistry;
import com.ichezzy.evolutionboost.quest.QuestManager;
import com.ichezzy.evolutionboost.quest.random.RandomQuestHook;
import com.ichezzy.evolutionboost.quest.random.RandomQuestManager;
import com.ichezzy.evolutionboost.quest.random.RandomQuestScheduler;
import com.ichezzy.evolutionboost.compat.cobblemon.QuestBattleHook;
import com.ichezzy.evolutionboost.compat.cobblemon.QuestCatchHook;
import com.ichezzy.evolutionboost.compat.cobblemon.QuestItemHook;
import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.util.DimensionRestrictions;
import com.ichezzy.evolutionboost.weather.ChristmasWeatherManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.item.Items;
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
        // S2C: HUD Toggle (Client soll HUD an/aus schalten)
        PayloadTypeRegistry.playS2C().register(HudTogglePayload.TYPE, HudTogglePayload.STREAM_CODEC);

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
                    AdminCommand.register(d);
                    RewardCommand.register(d);
                    BoostCommand.register(d);
                    WeatherCommand.register(d);
                    QuestCommand.register(d);
                    DexCommand.register(d);
                    NotificationCommand.register(d);
                    HudCommand.register(d);
                    GymCommand.register(d);
                }
        );

        // ---- Permissions registrieren (für LuckPerms) ----
        PermissionRegistry.register();

        // ---- Logging früh aktivieren ----
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CommandLogManager.init();
            LOGGER.info("[{}] Command logging initialized", MOD_ID);
        });

        // ---- Server gestartet ----
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            NotificationConfig.init(server); // Notification-Einstellungen laden
            RewardManager.init(server);
            BoostManager.get(server); // init/load
            QuestManager.get().init(server); // Quest-System initialisieren
            RandomQuestManager.get().init(server); // Random Quest System initialisieren
            RandomQuestScheduler.register(); // Reset-Benachrichtigungen registrieren
            DexDataManager.init(server); // Pokédex-Daten initialisieren
            GymManager.get().init(server); // Gym-System initialisieren

            // Trinkets-Kompatibilität initialisieren (falls Trinkets geladen)
            if (FabricLoader.getInstance().isModLoaded("trinkets")) {
                try {
                    com.ichezzy.evolutionboost.compat.trinkets.TrinketsCompat.init();
                } catch (NoClassDefFoundError e) {
                    LOGGER.warn("Trinkets mod detected but classes not found - skipping integration");
                }
            }

            if (FabricLoader.getInstance().isModLoaded("cobblemon")) {
                HooksRegistrar.register(server);
                // Quest-Hooks registrieren (benötigen Cobblemon)
                QuestBattleHook.register();
                QuestCatchHook.register();
                QuestItemHook.register();
                // Random Quest Hook registrieren
                RandomQuestHook.register();
                // Pokédex-Hook registrieren
                DexCatchHook.register();
                // Gym Battle Hook registrieren
                com.ichezzy.evolutionboost.gym.GymBattleHook.register();
                LOGGER.info("Cobblemon detected – hooks registered (incl. Quest, Random Quest, Dex & Gym hooks)");
            } else {
                LOGGER.warn("Cobblemon not detected – hooks skipped");
            }

            TicketManager.init(server);
        });

        // ---- Stop/Cleanup ----
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            NotificationConfig.shutdown();
            CommandLogManager.shutdown();
            GymManager.get().shutdown();
            safeUnregister(server);
        });

        // ---- Join-Hinweise ----
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler != null ? handler.player : null;
            if (player != null) {
                RewardManager.onPlayerJoin(player);
                
                // Alle Benachrichtigungen mit 2 Sekunden Verzögerung
                final UUID playerId = player.getUUID();
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(2000); // 2 Sekunden warten
                        server.execute(() -> {
                            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
                            if (onlinePlayer != null) {
                                // Gym Leader Benachrichtigungen
                                GymManager.get().onPlayerJoin(onlinePlayer);
                                
                                // Quest Benachrichtigungen
                                QuestManager.get().notifyAvailableQuests(onlinePlayer);
                                QuestManager.get().notifyReadyToTurnIn(onlinePlayer);
                                RandomQuestManager.get().notifyOnLogin(onlinePlayer);
                                
                                // Dex Benachrichtigungen
                                DexDataManager.notifyOnJoin(onlinePlayer);
                            }
                        });
                    } catch (InterruptedException ignored) {}
                });
            }
        });

        // ---- Fallschaden in beschränkten Dimensionen deaktivieren ----
        // (event:*, evolution:* außer evolution:quarry)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.is(DamageTypes.FALL)) {
                if (DimensionRestrictions.isFallDamageDisabled(entity.level())) {
                    return false; // Schaden verhindern
                }
            }
            return true; // Schaden erlauben
        });

        // ---- Raketen (Firework Rockets) in beschränkten Dimensionen blockieren ----
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }
            
            if (DimensionRestrictions.isFlightRestricted(world)) {
                if (player.getItemInHand(hand).is(Items.FIREWORK_ROCKET)) {
                    player.displayClientMessage(
                            Component.literal("⚠ Firework rockets are disabled in this dimension!")
                                    .withStyle(ChatFormatting.RED),
                            true
                    );
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }
            }
            
            return InteractionResultHolder.pass(player.getItemInHand(hand));
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
            
            // Elytra-Flug in beschränkten Dimensionen stoppen (alle 5 Ticks = 4x pro Sekunde)
            // Das ist ausreichend schnell und spart 80% Performance vs jeden Tick
            if (server.getTickCount() % 5 == 0) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (player.isFallFlying() && DimensionRestrictions.isFlightRestricted(player.level())) {
                        player.stopFallFlying();
                        player.displayClientMessage(
                                Component.literal("⚠ Elytra flight is disabled in this dimension!")
                                        .withStyle(ChatFormatting.RED),
                                true
                        );
                    }
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
        RandomQuestManager.get().shutdown(); // Random Quest-Daten speichern
        DexDataManager.shutdown(); // Pokédex-Daten speichern
    }
}
