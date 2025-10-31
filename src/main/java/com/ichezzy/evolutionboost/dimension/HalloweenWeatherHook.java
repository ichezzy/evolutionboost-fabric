package com.ichezzy.evolutionboost.dimension;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gewittersteuerung NUR für "event:halloween".
 * - startThunder(server, seconds)
 * - clearThunder(server)
 * - shinyBoostActiveFor/In helpers
 */
public final class HalloweenWeatherHook {
    private HalloweenWeatherHook() {}

    private static final ResourceKey<Level> HALLOWEEN_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:halloween"));

    // Countdown in Ticks
    private static final AtomicLong thunderTicksLeft = new AtomicLong(0);

    // alle N Ticks Wetterzustand erneut setzen, um "Drift" zu verhindern
    private static final int REASSERT_PERIOD = 20; // 1 Sekunde
    private static int reassertCounter = 0;

    public static void init(MinecraftServer server) {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            long left = thunderTicksLeft.get();
            if (left <= 0) return;

            ServerLevel sl = s.getLevel(HALLOWEEN_DIM);
            if (sl == null) return;

            // Re-assert zyklisch
            if (reassertCounter-- <= 0) {
                sl.setWeatherParameters(0, (int) left, true, true);
                reassertCounter = REASSERT_PERIOD;
            }

            if (thunderTicksLeft.decrementAndGet() <= 0) {
                clearThunder(s);
            }
        });
    }

    public static void startThunder(MinecraftServer server, int seconds) {
        ServerLevel sl = server.getLevel(HALLOWEEN_DIM);
        if (sl == null) {
            broadcast(server, Component.literal("[Halloween] Could not start storm: 'event:halloween' not loaded.")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        int durTicks = Math.max(1, seconds) * 20;
        thunderTicksLeft.set(durTicks);
        reassertCounter = 0;

        // sofortigen Zustand setzen
        sl.setWeatherParameters(0, durTicks, true, true);

        broadcast(server,
                Component.literal("[Halloween] A sinister storm is coming! Be prepared for rare hauntings...")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
    }

    public static void clearThunder(MinecraftServer server) {
        ServerLevel sl = server.getLevel(HALLOWEEN_DIM);
        thunderTicksLeft.set(0);
        reassertCounter = 0;

        if (sl != null) {
            // Klarwetter nur in dieser Dimension
            sl.setWeatherParameters(20 * 60 * 5, 0, false, false);
        }

        broadcast(server,
                Component.literal("[Halloween] The storm settles down... the rare appearances withdraw.")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** x2-Shiny aktiv für Spieler? */
    public static boolean shinyBoostActiveFor(net.minecraft.server.level.ServerPlayer player) {
        return thunderTicksLeft.get() > 0 && player.serverLevel().dimension().equals(HALLOWEEN_DIM);
    }
    /** x2-Shiny aktiv für Level? */
    public static boolean shinyBoostActiveIn(ServerLevel level) {
        return thunderTicksLeft.get() > 0 && level.dimension().equals(HALLOWEEN_DIM);
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(msg));
    }
}
