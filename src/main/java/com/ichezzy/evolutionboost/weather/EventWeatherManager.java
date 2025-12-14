package com.ichezzy.evolutionboost.weather;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public final class EventWeatherManager {

    private EventWeatherManager() {}

    /** Unsere Christmas-Dimension. */
    private static final ResourceKey<Level> CHRISTMAS_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:christmas"));

    private enum StormState {
        IDLE,      // kein Sturm
        PREPARE,   // „braut sich zusammen“ (1 Minute)
        ACTIVE     // eigentlicher Sturm (10 Minuten)
    }

    private static StormState christmasState = StormState.IDLE;
    /** Ticks seit Beginn des aktuellen State. */
    private static int christmasTicks = 0;

    // Zeiten in Ticks
    private static final int PREPARE_TICKS = 20 * 60;        // 1 Minute
    private static final int ACTIVE_TICKS  = 20 * 60 * 10;   // 10 Minuten
    private static final int CALM_MSG_TICK = 20 * 60 * 9;    // bei 9 Minuten „calming down“-Message

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EventWeatherManager::tickServer);
        EvolutionBoost.LOGGER.info("[weather] EventWeatherManager initialized.");
    }

    /* =========================================================
       Public API – Commands
       ========================================================= */

    /** /eb weather christmas storm on */
    public static void startChristmasStorm(MinecraftServer server) {
        if (server == null) return;

        christmasState = StormState.PREPARE;
        christmasTicks = 0;

        broadcastToChristmas(
                server,
                Component.literal("[Weather] A fierce blizzard is brewing in the Christmas Realm...")
                        .withStyle(ChatFormatting.AQUA)
        );

        EvolutionBoost.LOGGER.info("[weather] Christmas storm cycle STARTED (PREPARE).");
    }

    /** /eb weather christmas storm off */
    public static void stopChristmasStorm(MinecraftServer server) {
        if (server == null) return;

        clearChristmasWeather(server);
        christmasState = StormState.IDLE;
        christmasTicks = 0;

        broadcastToChristmas(
                server,
                Component.literal("[Weather] The storm over the Christmas Realm has cleared.")
                        .withStyle(ChatFormatting.GRAY)
        );

        EvolutionBoost.LOGGER.info("[weather] Christmas storm STOP requested (manual).");
    }

    /* =========================================================
       Tick-Logik
       ========================================================= */

    private static void tickServer(MinecraftServer server) {
        if (server == null) return;
        if (christmasState == StormState.IDLE) return;

        christmasTicks++;

        switch (christmasState) {
            case PREPARE -> tickChristmasPrepare(server);
            case ACTIVE -> tickChristmasActive(server);
        }
    }

    private static void tickChristmasPrepare(MinecraftServer server) {
        if (christmasTicks >= PREPARE_TICKS) {
            // in ACTIVE wechseln
            christmasState = StormState.ACTIVE;
            christmasTicks = 0;

            setChristmasWeather(server, true);

            broadcastToChristmas(
                    server,
                    Component.literal("[Weather] A howling blizzard engulfs the Christmas Realm!")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            );

            EvolutionBoost.LOGGER.info("[weather] Christmas storm ACTIVE.");
        }
    }

    private static void tickChristmasActive(MinecraftServer server) {
        // Effekte auf Spieler anwenden
        applyChristmasStormEffects(server);

        // Calm-down-Message nach 9 Minuten
        if (christmasTicks == CALM_MSG_TICK) {
            broadcastToChristmas(
                    server,
                    Component.literal("[Weather] The blizzard is beginning to calm down...")
                            .withStyle(ChatFormatting.AQUA)
            );
            EvolutionBoost.LOGGER.info("[weather] Christmas storm CALMING phase reached.");
        }

        // Ende nach 10 Minuten
        if (christmasTicks >= ACTIVE_TICKS) {
            clearChristmasWeather(server);
            christmasState = StormState.IDLE;
            christmasTicks = 0;

            broadcastToChristmas(
                    server,
                    Component.literal("[Weather] The storm over the Christmas Realm has faded.")
                            .withStyle(ChatFormatting.GRAY)
            );

            EvolutionBoost.LOGGER.info("[weather] Christmas storm finished (AUTO).");
        }
    }

    /* =========================================================
       Wetter setzen / löschen
       ========================================================= */

    private static ServerLevel getChristmasLevel(MinecraftServer server) {
        if (server == null) return null;
        return server.getLevel(CHRISTMAS_DIM);
    }

    private static void setChristmasWeather(MinecraftServer server, boolean thunder) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) {
            EvolutionBoost.LOGGER.warn("[weather] Christmas dimension not found (event:christmas).");
            return;
        }

        // Wetter lange genug halten, damit es nicht von Vanilla überschrieben wird
        int duration = ACTIVE_TICKS + PREPARE_TICKS + 20 * 60;

        if (thunder) {
            // (clearTime, rainTime, raining, thundering)
            level.setWeatherParameters(0, duration, true, true);
        } else {
            level.setWeatherParameters(duration, 0, false, false);
        }

        EvolutionBoost.LOGGER.info(
                "[weather] setWeatherParameters in event:christmas -> thunder={}, durationTicks={}",
                thunder, duration
        );
    }

    private static void clearChristmasWeather(MinecraftServer server) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) return;

        // Wetter auf klar setzen
        level.setWeatherParameters(20 * 60 * 5, 0, false, false);

        // Alle Spieler in der Dimension sofort „auftauen“
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() == level) {
                try {
                    player.setTicksFrozen(0);
                } catch (Throwable ignored) {}
            }
        }

        EvolutionBoost.LOGGER.info("[weather] Christmas weather cleared and players defrosted.");
    }

    /* =========================================================
       Effekte während des Sturms
       ========================================================= */

    private static void applyChristmasStormEffects(MinecraftServer server) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) continue;

            BlockPos pos = player.blockPosition();
            boolean openSky = level.canSeeSky(pos);
            boolean nearHeat = isNearHeatSource(level, pos, 6);

            try {
                int current = player.getTicksFrozen();

                if (openSky && !nearHeat) {
                    // Draußen & kalt -> einfrieren
                    int required = player.getTicksRequiredToFreeze();
                    int next = Math.min(required, current + 1); // langsam hoch
                    player.setTicksFrozen(next);
                } else {
                    // Unter Dach oder bei Feuer -> auftauen
                    int next = Math.max(0, current - 2);
                    player.setTicksFrozen(next);
                }
            } catch (Throwable t) {
                // Falls Mojang irgendwann Methoden umbenennt -> kein Crash
            }
        }
    }

    /**
     * Sehr einfache Heuristik: ein paar Wärmequellen in der Nähe?
     */
    private static boolean isNearHeatSource(ServerLevel level, BlockPos center, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    var state = level.getBlockState(cursor);

                    if (state.is(Blocks.CAMPFIRE)
                            || state.is(Blocks.SOUL_CAMPFIRE)
                            || state.is(Blocks.FIRE)
                            || state.is(Blocks.LAVA)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /* =========================================================
       Helper: Nachrichten
       ========================================================= */

    private static void broadcastToChristmas(MinecraftServer server, Component msg) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() == level) {
                player.sendSystemMessage(msg);
            }
        }
    }
}
