package com.ichezzy.evolutionboost.world;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.core.registries.Registries;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Einfacher, serverseitiger „Sturm-Controller“ für die Dimension event:halloween.
 * – 60-minütiger Zyklus: Ankündigung 5 Min vorher, dann 5 Min Sturm.
 * – Während Sturm kann ShinyHook optional x2 geben (siehe ShinyHook).
 */
public final class HalloweenWeatherHook {
    private HalloweenWeatherHook() {}

    // event:halloween Dimension-Key
    public static final ResourceKey<Level> HALLOWEEN_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:halloween"));

    // Zyklus
    private static final long CYCLE_MINUTES      = 60;          // alle 60 Minuten
    private static final long PREWARN_MINUTES    = 5;           // 5 Min vorher ankündigen
    private static final long STORM_MINUTES      = 5;           // 5 Min Gewitter

    private static Instant cycleStart = null;
    private static boolean prewarned = false;
    private static boolean storming  = false;

    // Bossbar-/Shiny-Flag
    private static boolean shinyBoostActive = false;

    public static void init(MinecraftServer server) {
        cycleStart = Instant.now();
        prewarned = false;
        storming = false;
        shinyBoostActive = false;

        // GameRules anpassen (nur Halloween-Dimension fixieren)
        // (Serverweite Regeln unangetastet lassen.)
        ServerTickEvents.END_SERVER_TICK.register(s -> tick(s));
    }

    @SuppressWarnings("resource")
    private static void tick(MinecraftServer server) {
        if (cycleStart == null) cycleStart = Instant.now();

        Duration since = Duration.between(cycleStart, Instant.now());
        long seconds = since.getSeconds();
        long cycleSeconds = CYCLE_MINUTES * 60;
        long prewarnAt = cycleSeconds - PREWARN_MINUTES * 60;
        long stormStart = cycleSeconds;
        long stormEnd   = cycleSeconds + STORM_MINUTES * 60;

        // Dimension holen
        ServerLevel halloween = server.getLevel(HALLOWEEN_DIM);
        if (halloween == null) return;

        // Zeit im Zyklus abbilden
        if (seconds < prewarnAt) {
            // idle
        } else if (seconds >= prewarnAt && seconds < stormStart) {
            // Vorwarnung
            if (!prewarned) {
                broadcastIn(halloween, Component.literal("[Halloween] A sinister storm is approaching in 5 minutes…").withStyle(s -> s.withColor(0x6A0DAD).withBold(true)));
                prewarned = true;
            }
        } else if (seconds >= stormStart && seconds < stormEnd) {
            // Sturm läuft
            if (!storming) {
                storming = true;
                shinyBoostActive = true;
                startThunder(halloween);
                broadcastIn(halloween, Component.literal("[Halloween] A sinister storm is here! Rare hauntings stir…").withStyle(s -> s.withColor(0x6A0DAD).withBold(true)));
            }
            // optional: Fortschritt anzeigen (Bossbar könntest du hier einbauen)
        } else {
            // Ende des Sturmfensters -> reset & nächste Runde
            if (storming) {
                stopThunder(halloween);
                broadcastIn(halloween, Component.literal("[Halloween] The storm settles down… the rare appearances withdraw.").withStyle(s -> s.withColor(0x808080).withItalic(true)));
            }
            storming = false;
            shinyBoostActive = false;
            prewarned = false;
            cycleStart = Instant.now(); // Neustart des Zyklus
        }
    }

    @SuppressWarnings("resource")
    private static void startThunder(ServerLevel level) {
        // Stelle Wetter in dieser Dimension auf Thunder
        // (In 1.21+ kannst du über LevelData/ServerLevel Methoden steuern)
        level.setWeatherParameters(0, (int) (STORM_MINUTES * 60 * 20), true, true);
    }

    @SuppressWarnings("resource")
    private static void stopThunder(ServerLevel level) {
        // Zurück zu klarem Wetter (oder normalem Regen=false)
        level.setWeatherParameters(0, 0, false, false);
    }

    private static void broadcastIn(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(msg);
        }
    }

    /** Für ShinyHook: Spielerbezogen prüfen, ob in Halloween-Dimension & Sturm aktiv. */
    public static boolean shinyBoostActiveFor(ServerPlayer player) {
        return shinyBoostActive && player.serverLevel() != null && player.serverLevel().dimension().equals(HALLOWEEN_DIM);
    }

    /** Für ShinyHook: Weltbezogen prüfen. */
    public static boolean shinyBoostActiveIn(ServerLevel sl) {
        return shinyBoostActive && sl != null && sl.dimension().equals(HALLOWEEN_DIM);
    }
}
