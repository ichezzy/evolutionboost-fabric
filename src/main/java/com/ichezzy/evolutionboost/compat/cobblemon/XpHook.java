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
 * Dimension-spezifischer XP-Boost (Cobblemon Battle-EXP).
 * - Togglebar via /halloweenxp on|off
 * - Wirkt NUR in dimension "event:halloween", sonst normal.
 * - Nutzt Reflection, damit es auch bei kleineren API-Änderungen robust bleibt.
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class XpHook {
    private XpHook() {}

    /** Toggle vom Command. */
    private static volatile boolean HALLOWEEN_XP_ENABLED = false;

    /** Ziel-Dimension: event:halloween */
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

            // BattleExperienceSource check
            try {
                Class<?> battleSrc = Class.forName("com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource");
                if (!battleSrc.isInstance(src)) return;
            } catch (ClassNotFoundException ignore) {
                if (!src.getClass().getName().toLowerCase().contains("battle")) return;
            }

            // ---- 2) Spieler/Level ermitteln, um Dimension zu prüfen ----
            ServerPlayer player = extractServerPlayer(ev, src);
            ServerLevel level  = (player != null) ? player.serverLevel() : extractServerLevel(ev, src, server);
            if (level == null) return;

            // ---- 3) Wenn Toggle aus -> nichts tun ----
            if (!HALLOWEEN_XP_ENABLED) return;

            // ---- 4) Nur in event:halloween boosten ----
            if (!Objects.equals(level.dimension(), HALLOWEEN_DIM)) return;

            // ---- 5) XP lesen, globaler Event-Boost anwenden, zurückschreiben ----
            Method getExp = find(ev.getClass(), "getExperience");
            Method setExp = find(ev.getClass(), "setExperience", int.class);
            if (getExp == null || setExp == null) return;

            int exp = ((Number) getExp.invoke(ev)).intValue();

            // Dein bestehendes Boost-System bleibt intakt (global/spielerbezogen);
            // wir multiplizieren ON TOP nur in dieser Dimension (x2).
            double mult = BoostManager.get(server).getMultiplierFor(BoostType.XP, null);
            mult *= 2.0; // dimension-spezifisch x2

            int boosted = Math.max(1, (int) Math.round(exp * mult));
            setExp.invoke(ev, boosted);

        } catch (Throwable t) {
            // bewusst still, damit bei API-Änderungen kein Crash entsteht
        }
    }

    /* -------------------------------- Helpers -------------------------------- */

    /** Versucht, einen ServerPlayer aus dem Event oder der Source zu holen. */
    private static ServerPlayer extractServerPlayer(Object ev, Object src) {
        try {
            // häufig direkt am Event
            Method m = findAny(ev.getClass(), "getPlayer", "player", "getServerPlayer", "serverPlayer");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerPlayer sp) return sp;
            }
        } catch (Throwable ignored) {}

        try {
            // manchmal hängt es an der Source
            Method m = findAny(src.getClass(), "getPlayer", "player", "getServerPlayer", "serverPlayer", "getTrainer");
            if (m != null) {
                Object o = m.invoke(src);
                if (o instanceof ServerPlayer sp) return sp;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /** Fallback: Wenn kein Player ermittelbar ist, versuche ein ServerLevel. */
    private static ServerLevel extractServerLevel(Object ev, Object src, MinecraftServer server) {
        try {
            // direkt am Event
            Method m = findAny(ev.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}

        try {
            // an der Source
            Method m = findAny(src.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(src);
                if (o instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}

        // absoluter Fallback: nimm Overworld, wenn vorhanden (nur um NPEs zu vermeiden)
        try {
            return server.overworld();
        } catch (Throwable ignored) {}
        return null;
    }
}
