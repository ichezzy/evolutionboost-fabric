package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

/**
 * Shiny-Chance-Hook (Reflection, robust gegen kleinere API-Änderungen).
 * Rechnet: FINAL = BASE × GLOBAL × DIMENSION × (optional PLAYER)
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class ShinyHook {
    private ShinyHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        // Kandidaten: SPAWN_SHINY_ROLL_EVENT_PRE o.ä. – wir gehen über Reflection.
        subscribeField(clsEvents, priority, "SHINY_ROLL_EVENT_PRE", ev -> {
            tryAdjustShiny(server, ev);
            return unit();
        });
        // Fallback-Namen, falls oben nicht existiert:
        subscribeField(clsEvents, priority, "POKEMON_SHINY_ROLL_EVENT_PRE", ev -> {
            tryAdjustShiny(server, ev);
            return unit();
        });
    }

    private static void tryAdjustShiny(MinecraftServer server, Object ev) {
        try {
            // Versuche Spieler & Welt zu ermitteln
            ServerPlayer player = tryPlayer(ev);
            ServerLevel  level  = tryLevel(ev, server);

            // Basischance holen
            Double baseChance = getDouble(ev, "getChance", "chance", "getShinyChance", "getBaseChance");
            Integer baseRolls = getInt(ev, "getRolls", "getShinyRolls");

            double mult = BoostManager.get(server)
                    .getMultiplierFor(BoostType.SHINY, player != null ? player.getUUID() : null, level != null ? level.dimension() : null);

            if (baseChance != null) {
                double newChance = Math.max(0.0, baseChance * mult);
                callSetter(ev, "setChance", double.class, newChance);
                callSetter(ev, "setShinyChance", double.class, newChance);
            }
            if (baseRolls != null) {
                int newRolls = (int) Math.max(1, Math.round(baseRolls * mult));
                callSetter(ev, "setRolls", int.class, newRolls);
                callSetter(ev, "setShinyRolls", int.class, newRolls);
            }
        } catch (Throwable ignored) {}
    }

    /* --------------- helpers --------------- */

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
