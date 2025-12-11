package com.ichezzy.evolutionboost.weather;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Wetter-Manager für Event-Dimensionen.
 *
 * Aktuell:
 *   - event:christmas
 *
 * Verhalten:
 *   - Standard: in event:christmas wird regelmäßig klares Wetter erzwungen
 *               (kein natürlicher Regen/Sturm dort).
 *
 *   - startChristmasStorm(server):
 *        Minute 0: Chat-Nachricht "Blizzard kommt"
 *        Minute 1: Gewitter / Schneesturm
 *        Minute 10: Chat-Nachricht "Blizzard legt sich"
 *        Minute 11: Wetter wieder klar + Sturm beendet
 */
public final class EventWeatherManager {

    // Dimension-Key für das Christmas-Event
    public static final ResourceKey<Level> CHRISTMAS_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("event", "christmas"));

    private static final int TICKS_PER_SECOND = 20;
    private static final int PREPARE_TICKS    = 60 * TICKS_PER_SECOND;       // 1 Minute
    private static final int STORM_TICKS      = 10 * 60 * TICKS_PER_SECOND;  // 10 Minuten
    private static final int END_MSG_TICKS    = PREPARE_TICKS + STORM_TICKS; // bei 11. Minute - 1
    private static final int STOP_TICKS       = END_MSG_TICKS + 60 * TICKS_PER_SECOND; // 11. Minute

    // Status für den Christmas-Sturm
    private static boolean stormRunning = false;
    private static int stormTick = 0;

    // Allgemeiner Tickzähler, um Clear-Wetter nicht jeden Tick zu setzen
    private static int tickCounter = 0;

    private EventWeatherManager() {}

    /** Vom Mod-Init aus aufrufen. Registriert den Server-Tick-Listener. */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EventWeatherManager::onServerTick);
        EvolutionBoost.LOGGER.info("[weather] EventWeatherManager initialised.");
    }

    /* ====================================================================== */
    /* Public API                                                             */
    /* ====================================================================== */

    /**
     * Startet einen kompletten Blizzard-Zyklus in event:christmas.
     * - Wenn bereits ein Sturm läuft, passiert nichts.
     */
    public static void startChristmasStorm(MinecraftServer server) {
        if (stormRunning) {
            EvolutionBoost.LOGGER.info("[weather] Christmas storm already running, ignoring start request.");
            return;
        }
        stormRunning = true;
        stormTick = 0;

        EvolutionBoost.LOGGER.info("[weather] Christmas storm START requested.");

        // Sofortige Start-Nachricht (Minute 0)
        broadcastInChristmas(server,
                Component.literal("[Weather] A fierce blizzard is approaching the Christmas event area!")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        );
    }

    /**
     * Bricht einen laufenden Blizzard ab und stellt sofort klares Wetter her.
     */
    public static void stopChristmasStorm(MinecraftServer server) {
        if (!stormRunning) {
            EvolutionBoost.LOGGER.info("[weather] Christmas storm stop requested, but no storm is running.");
        } else {
            EvolutionBoost.LOGGER.info("[weather] Christmas storm STOP requested.");
        }

        stormRunning = false;
        stormTick = 0;
        applyChristmasClear(server);
    }

    public static boolean isChristmasStormRunning() {
        return stormRunning;
    }

    /* ====================================================================== */
    /* Tick-Logik                                                             */
    /* ====================================================================== */

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;

        if (!stormRunning) {
            // Standard: event:christmas auf "klar" halten, damit dort kein normales Wetter aufkommt
            if (tickCounter % 200 == 0) { // alle 10 Sekunden
                applyChristmasClear(server);
            }
            return;
        }

        // Sturm läuft
        stormTick++;

        // 1) Nach 1 Minute: Gewitter/Schneesturm starten
        if (stormTick == PREPARE_TICKS) {
            EvolutionBoost.LOGGER.info("[weather] Christmas storm: entering active phase.");
            applyChristmasThunder(server);
        }

        // 2) Nach 10 Minuten Sturm: Ankündigung, dass er sich legt
        if (stormTick == END_MSG_TICKS) {
            EvolutionBoost.LOGGER.info("[weather] Christmas storm: nearing end.");
            broadcastInChristmas(server,
                    Component.literal("[Weather] The blizzard is starting to calm down...")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
            );
        }

        // 3) Nach 11 Minuten: Sturm beenden + klares Wetter
        if (stormTick >= STOP_TICKS) {
            EvolutionBoost.LOGGER.info("[weather] Christmas storm: finished; clearing weather.");
            stormRunning = false;
            stormTick = 0;
            applyChristmasClear(server);
        }
    }

    /* ====================================================================== */
    /* Wetter-Aktionen                                                        */
    /* ====================================================================== */

    /** Erzwingt in event:christmas Schneesturm / Gewitter. */
    private static void applyChristmasThunder(MinecraftServer server) {
        ServerLevel level = server.getLevel(CHRISTMAS_DIM);
        if (level == null) {
            EvolutionBoost.LOGGER.warn("[weather] Christmas dimension not found for thunder.");
            return;
        }

        // clearDuration = 0  -> sofort
        // rainDuration  = STORM_TICKS + etwas Puffer
        // raining       = true
        // thundering    = true (für dunklen Himmel & Donner)
        int duration = STORM_TICKS + 2 * TICKS_PER_SECOND;
        level.setWeatherParameters(0, duration, true, true);
    }

    /** Erzwingt in event:christmas klares Wetter (kein Regen, kein Sturm). */
    private static void applyChristmasClear(MinecraftServer server) {
        ServerLevel level = server.getLevel(CHRISTMAS_DIM);
        if (level == null) {
            EvolutionBoost.LOGGER.warn("[weather] Christmas dimension not found for clear.");
            return;
        }

        // clearDuration = 6000 (~5 Minuten), rainDuration = 0, raining = false, thundering = false
        level.setWeatherParameters(6000, 0, false, false);
    }

    /* ====================================================================== */
    /* Hilfsfunktionen                                                        */
    /* ====================================================================== */

    private static void broadcastInChristmas(MinecraftServer server, Component msg) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            if (p.serverLevel().dimension().equals(CHRISTMAS_DIM)) {
                p.sendSystemMessage(msg);
            }
        }
    }
}
