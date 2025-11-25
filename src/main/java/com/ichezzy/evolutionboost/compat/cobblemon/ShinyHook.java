package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

/**
 * Shiny-Chance-Hook (Reflection, robust gegen kleinere API-Änderungen).
 *
 * FINAL_SHINY_FACTOR = BASE × GLOBAL × DIMENSION × (optional PLAYER)
 *
 * - Wir hängen uns an das Cobblemon-Event für den Shiny-Roll.
 * - Konkreter Feldname kann je nach Version variieren, daher nutzen wir subscribeFieldOptional.
 */
@SuppressWarnings({"rawtypes","unchecked"})
public final class ShinyHook {
    private ShinyHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        // Kandidaten für das Shiny-Event-Feld in CobblemonEvents:
        // 1.6.x: typischerweise "SHINY_ROLL_EVENT"
        // ältere/andere Versionen evtl. mit *_PRE oder POKEMON_* Präfix.
        String[] shinyFieldCandidates = new String[]{
                "SHINY_ROLL_EVENT",
                "SHINY_ROLL_EVENT_PRE",
                "POKEMON_SHINY_ROLL_EVENT",
                "POKEMON_SHINY_ROLL_EVENT_PRE"
        };

        subscribeFieldOptional(clsEvents, priority, shinyFieldCandidates, ev -> {
            tryAdjustShiny(server, ev);
            return unit();
        });
    }

    /**
     * Versucht, die Shiny-Wahrscheinlichkeit / Shiny-Rolls des Events zu skalieren.
     * Nutzt:
     *   BoostManager.get(server).getMultiplierFor(SHINY, playerUuid, levelKey)
     */
    private static void tryAdjustShiny(MinecraftServer server, Object ev) {
        try {
            // Spieler & Welt ermitteln (falls vorhanden)
            ServerPlayer player = tryPlayer(ev);
            ServerLevel  level  = tryLevel(ev, server);

            // Basischance / Rolls holen
            Double baseChance = getDouble(ev,
                    "getChance", "chance",
                    "getShinyChance", "getBaseChance",
                    "getRollChance" // falls anders benannt
            );
            Integer baseRolls = getInt(ev,
                    "getRolls", "getShinyRolls", "rolls"
            );

            // Multiplikator aus unserem Boost-System
            double mult = BoostManager.get(server)
                    .getMultiplierFor(
                            BoostType.SHINY,
                            player != null ? player.getUUID() : null,
                            level != null ? level.dimension() : null
                    );

            // Chance skalieren
            if (baseChance != null) {
                double newChance = Math.max(0.0, baseChance * mult);
                callSetter(ev, "setChance",      double.class, newChance);
                callSetter(ev, "setShinyChance", double.class, newChance);
                // ggf. alternative Setter-Namen ergänzen, falls nötig
            }

            // Anzahl Rolls skalieren (immer >=1)
            if (baseRolls != null) {
                int newRolls = (int) Math.max(1, Math.round(baseRolls * mult));
                callSetter(ev, "setRolls",      int.class, newRolls);
                callSetter(ev, "setShinyRolls", int.class, newRolls);
            }

        } catch (Throwable ignored) {
            // Absicht: API-Änderungen sollen keinen Crash verursachen.
        }
    }

    /* --------------- helpers --------------- */

    private static ServerPlayer tryPlayer(Object ev) {
        try {
            Method m = findAny(ev.getClass(),
                    "getPlayer", "player",
                    "getServerPlayer", "serverPlayer",
                    "getTrainer"
            );
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
        try {
            return server.overworld();
        } catch (Throwable ignored) {}
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
            if (m != null) {
                m.setAccessible(true);
                m.invoke(ev, value);
            }
        } catch (Throwable ignored) {}
    }
}
