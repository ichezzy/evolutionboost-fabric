package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Objects;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

/**
 * XP-Boost (Cobblemon Battle-EXP) – kombiniert GLOBAL × DIMENSION × (optional PLAYER).
 * Alte Halloween-Toggle bleibt kompatibel, dimensionale Faktoren kommen aus BoostManager.
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class XpHook {
    private XpHook() {}

    /** Legacy-Toggle für Halloween (kann bleiben, wirkt zusätzlich zur neuen Dimension-Logik). */
    private static volatile boolean HALLOWEEN_XP_ENABLED = false;

    /** Ziel-Dimensionen (Legacy: Halloween). */
    private static final ResourceKey<net.minecraft.world.level.Level> HALLOWEEN_DIM =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath("event", "halloween"));

    public static void setHalloweenXpEnabled(boolean enabled) {
        HALLOWEEN_XP_ENABLED = enabled;
        EvolutionBoost.LOGGER.info("[EvolutionBoost] Halloween XP enabled = {}", enabled);
    }
    public static boolean isHalloweenXpEnabled() { return HALLOWEEN_XP_ENABLED; }

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        // Cobblemon Event: EXPERIENCE_GAINED_EVENT_PRE
        subscribeField(clsEvents, priority, "EXPERIENCE_GAINED_EVENT_PRE", ev -> {
            tryXp(server, ev);
            return unit();
        });
    }

    private static void tryXp(MinecraftServer server, Object ev) {
        try {
            // ---- 1) Quelle checken: nur Battle-XP boosten ----
            Method getSource = find(ev.getClass(), "getSource");
            if (getSource == null) return;
            Object src = getSource.invoke(ev);
            if (src == null) return;

            try {
                Class<?> battleSrc = Class.forName("com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource");
                if (!battleSrc.isInstance(src)) return;
            } catch (ClassNotFoundException ignore) {
                if (!src.getClass().getName().toLowerCase().contains("battle")) return;
            }

            // ---- 2) Spieler/Level für Dimension ----
            ServerPlayer player = extractServerPlayer(ev, src);
            ServerLevel level  = (player != null) ? player.serverLevel() : extractServerLevel(ev, src, server);
            if (level == null) return;

            // ---- 3) XP lesen / setzen ----
            Method getExp = find(ev.getClass(), "getExperience");
            Method setExp = find(ev.getClass(), "setExperience", int.class);
            if (getExp == null || setExp == null) return;

            int exp = ((Number) getExp.invoke(ev)).intValue();

            // ---- 4) Multiplikator: GLOBAL × DIMENSION × PLAYER ----
            var bm = BoostManager.get(server);
            double mult = bm.getMultiplierFor(BoostType.XP,
                    player != null ? player.getUUID() : null,
                    level.dimension());

            // Legacy: Wenn Halloween-Toggle manuell aktiv UND wir sind in Halloween-Dim, additiv x2 oben drauf
            if (HALLOWEEN_XP_ENABLED && Objects.equals(level.dimension(), HALLOWEEN_DIM)) {
                mult *= 2.0;
            }

            int boosted = Math.max(1, (int) Math.round(exp * mult));
            setExp.invoke(ev, boosted);

        } catch (Throwable ignored) {
            // still, um API-Änderungen zu tolerieren
        }
    }

    /* -------------------------------- Helpers -------------------------------- */

    private static ServerPlayer extractServerPlayer(Object ev, Object src) {
        try {
            Method m = findAny(ev.getClass(), "getPlayer", "player", "getServerPlayer", "serverPlayer");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerPlayer sp) return sp;
            }
        } catch (Throwable ignored) {}

        try {
            Method m = findAny(src.getClass(), "getPlayer", "player", "getServerPlayer", "serverPlayer", "getTrainer");
            if (m != null) {
                Object o = m.invoke(src);
                if (o instanceof ServerPlayer sp) return sp;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static ServerLevel extractServerLevel(Object ev, Object src, MinecraftServer server) {
        try {
            Method m = findAny(ev.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}

        try {
            Method m = findAny(src.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(src);
                if (o instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}

        try { return server.overworld(); } catch (Throwable ignored) {}
        return null;
    }
}
