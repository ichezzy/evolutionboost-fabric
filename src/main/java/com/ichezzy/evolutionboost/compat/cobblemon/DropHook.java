package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

/**
 * Drop-Hook (z. B. zusätzliche Loot-Chance oder Menge).
 * Wendet GLOBAL × DIMENSION × (optional PLAYER) als Multiplikator an.
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class DropHook {
    private DropHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        // Beispielhafte Eventnamen – Reflection macht’s robust:
        subscribeField(clsEvents, priority, "POKEMON_DROPS_EVENT_PRE", ev -> {
            tryAdjust(server, ev);
            return unit();
        });
        subscribeField(clsEvents, priority, "LOOT_EVENT_PRE", ev -> {
            tryAdjust(server, ev);
            return unit();
        });
    }

    private static void tryAdjust(MinecraftServer server, Object ev) {
        try {
            ServerPlayer player = tryPlayer(ev);
            ServerLevel  level  = tryLevel(ev, server);

            double mult = BoostManager.get(server)
                    .getMultiplierFor(BoostType.DROP, player != null ? player.getUUID() : null, level != null ? level.dimension() : null);

            // Strategie A: Drop-Chance skalieren
            Double chance = getDouble(ev, "getDropChance", "getChance");
            if (chance != null) {
                double newChance = Math.max(0.0, chance * mult);
                callSetter(ev, "setDropChance", double.class, newChance);
                callSetter(ev, "setChance", double.class, newChance);
            }

            // Strategie B: Menge skalieren (falls vorhanden)
            Integer count = getInt(ev, "getDropCount", "getCount");
            if (count != null && mult != 1.0) {
                int newCount = (int) Math.max(1, Math.round(count * mult));
                callSetter(ev, "setDropCount", int.class, newCount);
                callSetter(ev, "setCount", int.class, newCount);
            }
        } catch (Throwable ignored) {}
    }

    /* helpers (wie in ShinyHook, kurz gehalten) */

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

    private static void callSetter(Object ev, String name, Class<?> argType, Object value) {
        try {
            Method m = find(ev.getClass(), name, argType);
            if (m != null) m.invoke(ev, value);
        } catch (Throwable ignored) {}
    }
}
