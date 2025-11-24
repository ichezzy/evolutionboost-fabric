package com.ichezzy.evolutionboost.dimension;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;

public final class ChristmasEvent {
    private ChristmasEvent() {}

    private static final ResourceKey<Level> CHRISTMAS_DIM =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath("event", "christmas"));

    private static boolean stormActive = false;
    private static long nextStormTick = 0L;
    private static long stormEndTick = 0L;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (!(level instanceof ServerLevel sl)) return;
            if (!sl.dimension().equals(CHRISTMAS_DIM)) return;

            var cfg = EvolutionBoostConfig.get();
            long now = sl.getServer().getTickCount();

            if (!stormActive) {
                if (nextStormTick == 0L) {
                    nextStormTick = now + Math.max(1, cfg.christmasStormEveryMinutes) * 60L * 20L;
                }
                if (now >= nextStormTick) {
                    startStorm(sl);
                    stormEndTick = now + Math.max(1, cfg.christmasStormDurationMinutes) * 60L * 20L;
                    announce(sl, Component.literal("[Christmas] A blizzard is starting! Bundle up ❄")
                            .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                }
            } else {
                // Blizzard läuft
                doStormTick(sl);

                if (now >= stormEndTick) {
                    endStorm(sl);
                    announce(sl, Component.literal("[Christmas] The blizzard calms down.")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    nextStormTick = now + Math.max(1, cfg.christmasStormEveryMinutes) * 60L * 20L;
                }
            }
        });
    }

    // ----- Admin-APIs für Commands -----

    /** Startet sofort einen Blizzard (falls nicht aktiv). Gibt true bei Erfolg. */
    public static boolean forceStart(MinecraftServer server) {
        ServerLevel sl = server.getLevel(CHRISTMAS_DIM);
        if (sl == null || stormActive) return false;
        startStorm(sl);
        long now = server.getTickCount();
        stormEndTick = now + Math.max(1, EvolutionBoostConfig.get().christmasStormDurationMinutes) * 60L * 20L;
        announce(sl, Component.literal("[Christmas] Blizzard forced start.").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        return true;
    }

    /** Stoppt einen aktiven Blizzard sofort. Gibt true bei Erfolg. */
    public static boolean forceStop(MinecraftServer server) {
        ServerLevel sl = server.getLevel(CHRISTMAS_DIM);
        if (sl == null || !stormActive) return false;
        endStorm(sl);
        announce(sl, Component.literal("[Christmas] Blizzard forced stop.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        nextStormTick = server.getTickCount() + Math.max(1, EvolutionBoostConfig.get().christmasStormEveryMinutes) * 60L * 20L;
        return true;
    }

    /** Textstatus für /christmas storm status. */
    public static String status(MinecraftServer server) {
        ServerLevel sl = server.getLevel(CHRISTMAS_DIM);
        if (sl == null) return "[Christmas] Dimension event:christmas not loaded.";
        long now = server.getTickCount();
        if (stormActive) {
            long sec = Math.max(0, (stormEndTick - now) / 20L);
            return "[Christmas] Blizzard ACTIVE (" + sec + "s remaining).";
        } else {
            long sec = Math.max(0, (nextStormTick - now) / 20L);
            return "[Christmas] Blizzard idle. Next in ~" + sec + "s.";
        }
    }

    // ----- Intern -----

    private static void startStorm(ServerLevel sl) {
        stormActive = true;
        var cfg = EvolutionBoostConfig.get();
        var bm = BoostManager.get(sl.getServer());

        // dimensionale Boosts aktivieren (nur während Sturm)
        bm.setDimensionMultiplier(BoostType.XP, sl.dimension(), Math.max(0.0, cfg.christmasXpMultiplierDuringStorm));
        bm.setDimensionMultiplier(BoostType.SHINY, sl.dimension(), Math.max(0.0, cfg.christmasShinyMultiplierDuringStorm));

        if (cfg.christmasDebug) {
            EvolutionBoost.LOGGER.info("[christmas] storm started in {}", sl.dimension().location());
        }
    }

    private static void endStorm(ServerLevel sl) {
        stormActive = false;
        var bm = BoostManager.get(sl.getServer());
        // dimensionale Boosts wieder entfernen
        bm.clearAllDimensionMultipliers(sl.dimension());

        var cfg = EvolutionBoostConfig.get();
        if (cfg.christmasDebug) {
            EvolutionBoost.LOGGER.info("[christmas] storm ended in {}", sl.dimension().location());
        }
    }

    private static void doStormTick(ServerLevel sl) {
        // leichte serverseitige Effekte: dichter Schnee + kurzzeitige Debuffs
        var server = sl.getServer();
        if (server.getTickCount() % 5 == 0) {
            for (ServerPlayer p : sl.players()) {
                double x = p.getX(), y = p.getY() + 1.5, z = p.getZ();
                sl.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 40, 2.5, 1.5, 2.5, 0.01);
                sl.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 20, 2.5, 1.5, 2.5, 0.01);

                if (server.getTickCount() % 60 == 0) {
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, false, false));
                }
            }
        }
    }

    private static void announce(ServerLevel sl, Component msg) {
        for (ServerPlayer p : sl.players()) {
            p.sendSystemMessage(msg);
        }
    }

    public static boolean isStormActive() { return stormActive; }
}
