package com.ichezzy.evolutionboost.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
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

import java.lang.reflect.Method;
import java.util.Locale;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.find;
import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.findAny;

/**
 * XP-Hook:
 * - hängt direkt an CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE
 * - boostet NUR Battle-XP (BattleExperienceSource)
 * - verwendet GLOBAL × DIMENSION-Multiplikator aus BoostManager
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class XpHook {
    private XpHook() {}

    /**
     * Alte Signatur für HooksRegistrar – clsEvents/priority werden nicht mehr benötigt.
     */
    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        register(server);
    }

    /** Eigentliche Registrierung am Cobblemon-Event. */
    private static void register(MinecraftServer server) {
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(
                Priority.NORMAL,
                new Function1<Object, Unit>() {
                    @Override
                    public Unit invoke(Object ev) {
                        try {
                            handleXp(server, ev);
                        } catch (Throwable t) {
                            EvolutionBoost.LOGGER.warn(
                                    "[compat][xp] error in xp handler: {}",
                                    t.toString()
                            );
                        }
                        return Unit.INSTANCE;
                    }
                }
        );

        EvolutionBoost.LOGGER.info("[compat][xp] EXPERIENCE_GAINED_EVENT_PRE hook registered.");
    }

    /**
     * Wird bei jedem ExperienceGainedEvent.Pre aufgerufen.
     * Erwartet:
     *   - ev.getSource(): BattleExperienceSource (oder Klassenname enthält 'battle')
     *   - ev.getExperience()/setExperience(int): XP-Menge
     */
    private static void handleXp(MinecraftServer server, Object ev) {
        // --- 1) Quelle prüfen: nur Battle-XP ---
        Object src = invokeNoArg(ev, "getSource", "source");
        if (src == null) {
            return;
        }
        if (!isBattleExperienceSource(src)) {
            // z.B. XP-Candies o.ä. -> ignorieren
            return;
        }

        // --- 2) Player & Level bestimmen ---
        ServerPlayer player = extractServerPlayer(ev, src);
        ServerLevel level = (player != null)
                ? player.serverLevel()
                : extractServerLevel(ev, src, server);

        if (level == null) {
            return;
        }

        ResourceKey<Level> dimKey = level.dimension();

        // --- 3) Baseline-XP lesen ---
        Integer baseXp = readInt(ev, "getExperience", "getExp", "experience");
        if (baseXp == null || baseXp <= 0) {
            return;
        }

        // --- 4) Booster anwenden: GLOBAL × DIMENSION ---
        BoostManager bm = BoostManager.get(server);

        double globalMult = bm.getMultiplierFor(BoostType.XP, null); // nur globale
        double dimMult = bm.getDimensionMultiplier(BoostType.XP, dimKey);
        double mult = globalMult * dimMult;

        EvolutionBoost.LOGGER.debug(
                "[compat][xp][debug] dim={} baseXp={} globalMult={} dimMult={} totalMult={}",
                dimKey.location(), baseXp, globalMult, dimMult, mult
        );

        if (mult <= 1.0) {
            return; // kein Boost aktiv
        }

        int boosted = Math.max(1, (int) Math.round(baseXp * mult));

        // --- 5) Zurück ins Event schreiben ---
        boolean ok = writeInt(ev, boosted, "setExperience", "setExp");
        if (!ok) {
            return;
        }

        EvolutionBoost.LOGGER.debug(
                "[compat][xp] boosted battle XP in {} from {} to {} (mult={})",
                dimKey.location(), baseXp, boosted, mult
        );
    }

    /* ------------------------------------------------------------------ */
    /* Helper                                                             */
    /* ------------------------------------------------------------------ */

    /** Prüft möglichst robust, ob die Source Battle-XP ist. */
    private static boolean isBattleExperienceSource(Object src) {
        try {
            Class<?> battleSrc = Class.forName(
                    "com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource"
            );
            if (battleSrc.isInstance(src)) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // Fallback unten
        }

        String name = src.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("battle");
    }

    private static Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) return null;
        for (String n : methodNames) {
            try {
                Method m = findAny(target.getClass(), n);
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
                Method m = find(target.getClass(), n);
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
        boolean written = false;
        for (String n : methodNames) {
            try {
                Method m = find(target.getClass(), n, int.class);
                if (m != null) {
                    m.invoke(target, value);
                    written = true;
                }
            } catch (Throwable ignored) {}
        }
        return written;
    }

    private static ServerPlayer extractServerPlayer(Object ev, Object src) {
        // a) direkt aus dem Event
        try {
            Method m = findAny(ev.getClass(),
                    "getPlayer", "player",
                    "getServerPlayer", "serverPlayer"
            );
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerPlayer sp) {
                    return sp;
                }
            }
        } catch (Throwable ignored) {}

        // b) aus der Source
        try {
            Method m = findAny(src.getClass(),
                    "getPlayer", "player",
                    "getServerPlayer", "serverPlayer",
                    "getTrainer"
            );
            if (m != null) {
                Object o = m.invoke(src);
                if (o instanceof ServerPlayer sp) {
                    return sp;
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static ServerLevel extractServerLevel(Object ev, Object src, MinecraftServer server) {
        // a) Level im Event
        try {
            Method m = findAny(ev.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(ev);
                if (o instanceof ServerLevel sl) {
                    return sl;
                }
            }
        } catch (Throwable ignored) {}

        // b) Level in der Source
        try {
            Method m = findAny(src.getClass(), "getLevel", "getWorld", "level", "world");
            if (m != null) {
                Object o = m.invoke(src);
                if (o instanceof ServerLevel sl) {
                    return sl;
                }
            }
        } catch (Throwable ignored) {}

        // c) Fallback: Overworld
        try {
            return server.overworld();
        } catch (Throwable ignored) {}

        return null;
    }
}
