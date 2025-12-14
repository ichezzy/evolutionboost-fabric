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
import java.util.UUID;

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

    private static final boolean DEBUG_XP = true; // Bei Bedarf auf false setzen

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
        if (server == null || ev == null) {
            return;
        }

        // --- 0) Pokemon aus dem Event holen (für Owner-Fallback) ---
        Object pokemon = extractPokemon(ev);

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
        ServerPlayer player = extractServerPlayer(ev, src, pokemon, server);
        ServerLevel level = (player != null)
                ? player.serverLevel()
                : extractServerLevel(ev, src, server);

        if (level == null) {
            if (DEBUG_XP) {
                EvolutionBoost.LOGGER.info(
                        "[compat][xp][debug] no ServerLevel found for event={}, source={}, pokemon={}",
                        ev.getClass().getName(),
                        src.getClass().getName(),
                        pokemon != null ? pokemon.getClass().getName() : "null"
                );
            }
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
        double mult = bm.getMultiplierFor(BoostType.XP, null, dimKey);

        if (DEBUG_XP) {
            EvolutionBoost.LOGGER.info(
                    "[compat][xp][debug] dim={} baseXp={} mult={}",
                    dimKey.location(),
                    baseXp,
                    mult
            );
        }

        if (mult <= 1.0) {
            return; // kein Boost aktiv
        }

        int boosted = Math.max(1, (int) Math.round(baseXp * mult));

        // --- 5) Zurück ins Event schreiben ---
        boolean ok = writeInt(ev, boosted, "setExperience", "setExp");
        if (!ok) {
            return;
        }

        EvolutionBoost.LOGGER.info(
                "[compat][xp] boosted battle XP in {} from {} to {} (mult={})",
                dimKey.location(), baseXp, boosted, mult
        );
    }

    /* ------------------------------------------------------------------ */
    /* Battle-Source-Check                                                */
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

    /* ------------------------------------------------------------------ */
    /* Reflection-Helper für Werte                                        */
    /* ------------------------------------------------------------------ */

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

    /* ------------------------------------------------------------------ */
    /* Spieler / Dimension                                                */
    /* ------------------------------------------------------------------ */

    private static ServerPlayer extractServerPlayer(Object ev, Object src, Object pokemon, MinecraftServer server) {
        // 1) direkte Player-Referenz im Event
        ServerPlayer p = extractDirectPlayer(ev);
        if (p != null) return p;

        // 2) direkte Player-Referenz in der Source
        p = extractDirectPlayer(src);
        if (p != null) return p;

        // 3) Player-UUID im Event
        UUID id = extractPlayerId(ev);
        if (id != null) {
            ServerPlayer fromId = server.getPlayerList().getPlayer(id);
            if (fromId != null) return fromId;
        }

        // 4) Player-UUID in der Source
        id = extractPlayerId(src);
        if (id != null) {
            ServerPlayer fromId = server.getPlayerList().getPlayer(id);
            if (fromId != null) return fromId;
        }

        // 5) Fallback: Besitzer über das Pokemon
        if (pokemon != null) {
            ServerPlayer fromPokemon = extractPlayerFromPokemon(pokemon, server);
            if (fromPokemon != null) return fromPokemon;
        }

        // 6) nichts gefunden -> null (Level wird dann separat bestimmt)
        return null;
    }

    private static ServerPlayer extractDirectPlayer(Object obj) {
        if (obj == null) return null;
        try {
            Method m = findAny(obj.getClass(),
                    "getPlayer", "player",
                    "getServerPlayer", "serverPlayer",
                    "getUser", "user",
                    "getTrainer"
            );
            if (m != null) {
                Object o = m.invoke(obj);
                if (o instanceof ServerPlayer sp) {
                    return sp;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static UUID extractPlayerId(Object obj) {
        if (obj == null) return null;
        String[] candidates = {
                "getPlayerId", "getPlayerUUID", "getUuid", "getUUID", "getPlayerUuid", "playerId"
        };
        for (String name : candidates) {
            try {
                Method m = findAny(obj.getClass(), name);
                if (m == null) continue;
                Object o = m.invoke(obj);
                if (o instanceof UUID u) return u;
                if (o instanceof String s) {
                    try {
                        return UUID.fromString(s);
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (Throwable ignored) {}
        }
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

    /* ------------------------------------------------------------------ */
    /* Pokemon / Owner                                                    */
    /* ------------------------------------------------------------------ */

    private static Object extractPokemon(Object ev) {
        if (ev == null) return null;
        // Versuche getPokemon()
        try {
            Method m = findAny(ev.getClass(), "getPokemon", "pokemon");
            if (m != null) {
                return m.invoke(ev);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Versucht über das Pokemon den Besitzer (ServerPlayer) herauszufinden.
     * Wir probieren verschiedene übliche Methoden/Patterns:
     *  - getOwnerId(), getOwnerUUID() -> UUID
     *  - getOwner() -> ServerPlayer oder etwas, das einen Spieler referenziert
     */
    private static ServerPlayer extractPlayerFromPokemon(Object pokemon, MinecraftServer server) {
        if (pokemon == null || server == null) return null;

        // 1) UUID-basierte Owner-Methoden
        String[] idMethods = {
                "getOwnerId", "getOwnerUUID", "getOwnerUuid", "getOwnerPlayerUUID"
        };
        for (String name : idMethods) {
            try {
                Method m = findAny(pokemon.getClass(), name);
                if (m == null) continue;
                Object o = m.invoke(pokemon);
                UUID uuid = null;
                if (o instanceof UUID u) {
                    uuid = u;
                } else if (o instanceof String s) {
                    try {
                        uuid = UUID.fromString(s);
                    } catch (IllegalArgumentException ignored) {}
                }
                if (uuid != null) {
                    ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                    if (sp != null) {
                        if (DEBUG_XP) {
                            EvolutionBoost.LOGGER.info(
                                    "[compat][xp][debug] resolved player via Pokemon owner UUID {} -> {}",
                                    uuid, sp.getGameProfile().getName()
                            );
                        }
                        return sp;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2) Direktes Owner-Objekt
        String[] ownerMethods = {
                "getOwner", "owner", "getOwnerPlayer", "ownerPlayer"
        };
        for (String name : ownerMethods) {
            try {
                Method m = findAny(pokemon.getClass(), name);
                if (m == null) continue;
                Object o = m.invoke(pokemon);
                if (o instanceof ServerPlayer sp) {
                    if (DEBUG_XP) {
                        EvolutionBoost.LOGGER.info(
                                "[compat][xp][debug] resolved player via Pokemon owner object {}",
                                sp.getGameProfile().getName()
                        );
                    }
                    return sp;
                }
                // Falls in Zukunft etwas wie "PokemonOwner" zurückkommt,
                // könnte man hier weitere Reflection ansetzen (getPlayer(), getUuid(), ...)
            } catch (Throwable ignored) {}
        }

        return null;
    }
}
