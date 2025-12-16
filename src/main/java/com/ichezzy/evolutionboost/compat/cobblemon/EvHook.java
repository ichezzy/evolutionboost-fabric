package com.ichezzy.evolutionboost.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * EV-Hook:
 * - hängt an CobblemonEvents.EV_GAINED_EVENT_PRE (Cobblemon 1.7+)
 * - multipliziert die EV-Menge anhand des EV-Boosts (GLOBAL × DIMENSION)
 * - funktioniert für Battle-EVs, Item-EVs, etc.
 * - Komplett Reflection-basiert für Kotlin-Kompatibilität
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class EvHook {

    private EvHook() {}

    private static final boolean DEBUG_EV = true;

    /**
     * Alte Signatur für HooksRegistrar – clsEvents/priority werden nicht mehr benötigt.
     */
    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        register(server);
    }

    /** Eigentliche Registrierung am Cobblemon-Event via Reflection. */
    private static void register(MinecraftServer server) {
        try {
            // Event dynamisch holen (existiert erst ab Cobblemon 1.7)
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Field eventField = eventsClass.getField("EV_GAINED_EVENT_PRE");
            Object eventObservable = eventField.get(null);

            // subscribe Methode finden
            Method subscribeMethod = null;
            for (Method m : eventObservable.getClass().getMethods()) {
                if (m.getName().equals("subscribe") && m.getParameterCount() == 2) {
                    subscribeMethod = m;
                    break;
                }
            }

            if (subscribeMethod == null) {
                EvolutionBoost.LOGGER.warn("[compat][ev] Could not find subscribe method");
                return;
            }

            // Handler registrieren
            subscribeMethod.invoke(eventObservable, Priority.NORMAL, new Function1<Object, Unit>() {
                @Override
                public Unit invoke(Object ev) {
                    try {
                        handleEvGain(server, ev);
                    } catch (Throwable t) {
                        EvolutionBoost.LOGGER.warn(
                                "[compat][ev] error in ev handler: {}",
                                t.toString()
                        );
                    }
                    return Unit.INSTANCE;
                }
            });

            EvolutionBoost.LOGGER.info("[compat][ev] EV_GAINED_EVENT_PRE hook registered.");

        } catch (NoSuchFieldException e) {
            EvolutionBoost.LOGGER.info("[compat][ev] EV event not available (requires Cobblemon 1.7+)");
        } catch (Throwable t) {
            EvolutionBoost.LOGGER.warn("[compat][ev] Failed to register EV hook: {}", t.toString());
        }
    }

    /**
     * Wird bei jedem EV-Gain aufgerufen.
     */
    private static void handleEvGain(MinecraftServer server, Object ev) {
        if (server == null || ev == null) {
            return;
        }

        // --- 1) Basis-EV-Menge lesen ---
        Integer baseAmount = readInt(ev, "getAmount", "amount");
        if (baseAmount == null || baseAmount <= 0) {
            return;
        }

        // --- 2) Stat für Debug-Logging ---
        Object stat = invokeNoArg(ev, "getStat", "stat");
        String statName = stat != null ? stat.toString() : "unknown";

        // --- 3) Source aus Event holen ---
        Object source = invokeNoArg(ev, "getSource", "source");

        // --- 4) Dimension ermitteln ---
        ResourceKey<Level> dimKey = extractDimension(ev, source, server);

        // --- 5) Multiplier bestimmen (GLOBAL × DIMENSION) ---
        BoostManager bm = BoostManager.get(server);
        double mult = bm.getMultiplierFor(BoostType.EV, null, dimKey);

        if (DEBUG_EV) {
            EvolutionBoost.LOGGER.info(
                    "[compat][ev][debug] stat={} baseAmount={} dim={} mult={}",
                    statName,
                    baseAmount,
                    dimKey.location(),
                    mult
            );
        }

        if (mult <= 1.0) {
            return; // kein EV-Boost aktiv
        }

        // --- 6) Neuen Wert berechnen und setzen ---
        int boosted = (int) Math.round(baseAmount * mult);

        // EV setzen via setAmount(int)
        boolean ok = writeInt(ev, boosted, "setAmount");
        if (!ok) {
            EvolutionBoost.LOGGER.warn("[compat][ev] Could not set boosted EV amount");
            return;
        }

        EvolutionBoost.LOGGER.info(
                "[compat][ev] boosted EV gain for {} from {} to {} (mult={})",
                statName, baseAmount, boosted, mult
        );
    }

    /**
     * Versucht die Dimension aus dem Event/Source zu ermitteln.
     * Fallback: Overworld
     */
    private static ResourceKey<Level> extractDimension(Object ev, Object source, MinecraftServer server) {
        // 1) Versuche Pokemon aus Event oder Source zu holen
        Object pokemon = invokeNoArg(ev, "getPokemon", "pokemon");
        if (pokemon == null && source != null) {
            pokemon = invokeNoArg(source, "getPokemon", "pokemon");
        }

        if (pokemon != null) {
            // Owner UUID aus Pokemon -> Player -> Dimension
            UUID ownerUuid = extractOwnerUuid(pokemon);
            if (ownerUuid != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
                if (player != null) {
                    return player.serverLevel().dimension();
                }
            }

            // Entity aus Pokemon (falls in Welt)
            Object entity = invokeNoArg(pokemon, "getEntity", "entity");
            if (entity instanceof net.minecraft.world.entity.Entity e && e.level() instanceof ServerLevel sl) {
                return sl.dimension();
            }
        }

        // 2) Versuche Player direkt aus Source zu holen (ItemEvSource hat player)
        if (source != null) {
            Object playerObj = invokeNoArg(source, "getPlayer", "player");
            if (playerObj instanceof ServerPlayer sp) {
                return sp.serverLevel().dimension();
            }
        }

        // 3) Fallback: Overworld
        return Level.OVERWORLD;
    }

    /* ------------------------------------------------------------------ */
    /* Reflection-Helper                                                  */
    /* ------------------------------------------------------------------ */

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) return null;
        for (String n : methodNames) {
            try {
                Method m = findMethod(target.getClass(), n);
                if (m != null) {
                    Object res = m.invoke(target);
                    if (res != null) return res;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Integer readInt(Object target, String... methodNames) {
        if (target == null) return null;
        for (String n : methodNames) {
            try {
                Method m = findMethod(target.getClass(), n);
                if (m != null) {
                    Object v = m.invoke(target);
                    if (v instanceof Number num) {
                        return num.intValue();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean writeInt(Object target, int value, String... methodNames) {
        if (target == null) return false;
        for (String n : methodNames) {
            try {
                Method m = findMethod(target.getClass(), n, int.class);
                if (m != null) {
                    m.invoke(target, value);
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static UUID extractOwnerUuid(Object pokemon) {
        if (pokemon == null) return null;

        String[] methodNames = {"getOwnerUUID", "getOwnerUuid", "getOwnerId", "ownerUUID"};
        for (String name : methodNames) {
            try {
                Method m = findMethod(pokemon.getClass(), name);
                if (m != null) {
                    Object o = m.invoke(pokemon);
                    if (o instanceof UUID u) {
                        return u;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Findet eine Methode in der Klasse oder deren Superklassen/Interfaces.
     */
    private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null) return null;

        // Direkt in der Klasse suchen
        try {
            Method m = clazz.getMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {}

        // In deklarierten Methoden suchen (auch private)
        try {
            Method m = clazz.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException ignored) {}

        // Rekursiv in Superklasse suchen
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            Method m = findMethod(superClass, name, paramTypes);
            if (m != null) return m;
        }

        // In Interfaces suchen
        for (Class<?> iface : clazz.getInterfaces()) {
            Method m = findMethod(iface, name, paramTypes);
            if (m != null) return m;
        }

        return null;
    }
}