package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

/**
 * IV-Hook – skaliert z. B. die Anzahl perfekter IV-Rolls oder einen Bonus-Wert.
 * Nutzt GLOBAL × DIMENSION × (optional PLAYER).
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class IvHook {
    private IvHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        subscribeField(clsEvents, priority, "IV_ROLL_EVENT_PRE", ev -> {
            tryAdjust(server, ev);
            return unit();
        });
        // Fallback-Namen
        subscribeField(clsEvents, priority, "POKEMON_IV_EVENT_PRE", ev -> {
            tryAdjust(server, ev);
            return unit();
        });
    }

    private static void tryAdjust(MinecraftServer server, Object ev) {
        try {
            ServerPlayer player = tryPlayer(ev);
            ServerLevel  level  = tryLevel(ev, server);

            double mult = BoostManager.get(server)
                    .getMultiplierFor(BoostType.IV, player != null ? player.getUUID() : null, level != null ? level.dimension() : null);

            // Variante A: perfekte IV-Rolls erhöhen
            Integer perfect = getInt(ev, "getPerfectIvRolls", "getGuaranteedPerfectIVs", "getPerfects");
            if (perfect != null && mult != 1.0) {
                int boosted = (int) Math.max(0, Math.round(perfect * mult));
                callSetter(ev, "setPerfectIvRolls", int.class, boosted);
                callSetter(ev, "setGuaranteedPerfectIVs", int.class, boosted);
                callSetter(ev, "setPerfects", int.class, boosted);
                return;
            }

            // Variante B: generischer Bonuswert
            Double bonus = getDouble(ev, "getIvBonus", "getBonus");
            if (bonus != null && mult != 1.0) {
                double boosted = Math.max(0.0, bonus * mult);
                callSetter(ev, "setIvBonus", double.class, boosted);
                callSetter(ev, "setBonus", double.class, boosted);
            }
        } catch (Throwable ignored) {}
    }

    /* helpers */

    private static ServerPlayer tryPlayer(Object ev) {
        try {
            Method m = findAny(ev.getClass(), "getPlayer", "player", "getServerPlayer", "serverPlayer", "getTrainer");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerPlayer sp) return sp;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ServerLevel tryLevel(Object ev, MinecraftServer server) {
        try {
            Method m = findAny(ev.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerLevel sl) return sl;
            }
        } catch (Throwable ignored) {}
        try { return server.overworld(); } catch (Throwable ignored) {}
        return null;
    }

    private static Integer getInt(Object ev, String... names) {
        for (String n : names) {
            try {
                Method m = find(ev.getClass(), n);
                if (m != null) {
                    Object v = m.invoke(ev);
                    if (v instanceof Number num) return num.intValue();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Double getDouble(Object ev, String... names) {
        for (String n : names) {
            try {
                Method m = find(ev.getClass(), n);
                if (m != null) {
                    Object v = m.invoke(ev);
                    if (v instanceof Number num) return num.doubleValue();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static void callSetter(Object ev, String name, Class<?> argType, Object value) {
        try {
            Method m = find(ev.getClass(), name, argType);
            if (m != null) m.invoke(ev, value);
        } catch (Throwable ignored) {}
    }
}
