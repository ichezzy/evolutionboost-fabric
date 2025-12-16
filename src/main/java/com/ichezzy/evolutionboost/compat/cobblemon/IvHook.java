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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * IV-Hook:
 * - hängt an CobblemonEvents.POKEMON_GAINED
 * - erhöht die Chance auf perfekte IVs (31) pro Stat je nach BoostType.IV
 *
 * Idee:
 *   Basis: jeder Stat hat ~1/32 Chance auf 31 (uniform 0..31).
 *   Bei Multiplier M wollen wir ~M/32 als Endwahrscheinlichkeit.
 *   Dazu:
 *     - Spiel rollt IVs normal.
 *     - Wenn Stat != 31, upgraden wir mit Zusatz-Chance delta.
 *     - delta wird so berechnet, dass P(perfect) ≈ M * (1/32) wird.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class IvHook {

    private IvHook() {}

    /**
     * Alte Signatur für HooksRegistrar – clsEvents/priority werden nicht mehr benötigt.
     */
    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        register(server);
    }

    /** Eigentliche Registrierung am Cobblemon-Event. */
    private static void register(MinecraftServer server) {
        CobblemonEvents.POKEMON_GAINED.subscribe(
                Priority.NORMAL,
                new Function1<Object, Unit>() {
                    @Override
                    public Unit invoke(Object ev) {
                        try {
                            handleIv(server, ev);
                        } catch (Throwable t) {
                            EvolutionBoost.LOGGER.warn(
                                    "[compat][iv] error in iv handler: {}",
                                    t.toString()
                            );
                        }
                        return Unit.INSTANCE;
                    }
                }
        );

        EvolutionBoost.LOGGER.info("[compat][iv] POKEMON_GAINED hook registered.");
    }

    /* ------------------------------------------------------------------ */
    /* Hauptlogik                                                         */
    /* ------------------------------------------------------------------ */

    private static void handleIv(MinecraftServer server, Object ev) throws Exception {
        if (server == null || ev == null) {
            return;
        }

        // --- 1) Pokémon aus dem Event holen ---
        Object pokemon = extractPokemon(ev);
        if (pokemon == null) {
            return;
        }

        // --- 2) Dimension ermitteln (mehrere Fallbacks) ---
        ResourceKey<Level> dimKey = extractDimension(ev, pokemon, server);

        // --- 3) Multiplier bestimmen (GLOBAL × DIMENSION) ---
        BoostManager bm = BoostManager.get(server);
        double mult = bm.getMultiplierFor(BoostType.IV, null, dimKey);

        if (mult <= 1.0) {
            // kein IV-Boost aktiv
            return;
        }

        // Optionale Obergrenze, falls jemand 100x IV eingibt
        if (mult > 16.0) {
            mult = 16.0;
        }

        // --- 4) Zusatz-Chance delta berechnen ---
        // Basis: uniform 0..31 => P(31) = 1/32
        double baseP = 1.0 / 32.0;
        double targetP = Math.min(1.0, mult * baseP);

        if (targetP <= baseP + 1e-9) {
            // effektiver Multiplier zu klein, um sinnvoll etwas zu machen
            return;
        }

        double delta = (targetP - baseP) / (1.0 - baseP);
        if (delta <= 0.0) {
            return;
        }

        // --- 5) Reflektion: IVs-Objekt + Methoden ---
        Class<?> pokemonClass = pokemon.getClass();
        Method getIvsMethod = pokemonClass.getMethod("getIvs");
        Object ivs = getIvsMethod.invoke(pokemon);
        if (ivs == null) {
            return;
        }

        Class<?> statClass = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stat");
        Method ivsGet = ivs.getClass().getMethod("get", statClass);
        Method setIvMethod = pokemonClass.getMethod("setIV", statClass, int.class);

        // --- 6) Stats-Enum (HP, ATTACK, DEFENCE, SPECIAL_ATTACK, SPECIAL_DEFENCE, SPEED) ---
        Class<?> statsEnum = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");
        Object[] allStats = statsEnum.getEnumConstants();
        if (allStats == null || allStats.length == 0) {
            return;
        }

        List<Object> permanentStats = new ArrayList<>();
        for (Object s : allStats) {
            if (!(s instanceof Enum<?> e)) continue;
            String name = e.name();
            // EVASION/ACCURACY sind Battle-only und haben keine IVs
            if ("EVASION".equals(name) || "ACCURACY".equals(name)) {
                continue;
            }
            permanentStats.add(s);
        }

        if (permanentStats.isEmpty()) {
            return;
        }

        // Random-Quelle
        java.util.Random rand = new java.util.Random();

        // --- 7) Pro Stat: ggf. IV auf 31 hochziehen ---
        for (Object stat : permanentStats) {
            try {
                Object currentObj = ivsGet.invoke(ivs, stat);
                if (!(currentObj instanceof Number num)) {
                    continue;
                }
                int current = num.intValue();

                // Bereits perfekt? Dann nicht anfassen.
                if (current >= 31) {
                    continue;
                }

                if (rand.nextDouble() < delta) {
                    setIvMethod.invoke(pokemon, stat, 31);
                }
            } catch (Throwable inner) {
                // pro Stat loggen wir nichts, um Spam zu vermeiden, nur weiter zum nächsten
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Dimension-Extraktion mit mehreren Fallbacks                        */
    /* ------------------------------------------------------------------ */

    /**
     * Versucht die Dimension aus verschiedenen Quellen zu ermitteln:
     * 1. Player aus Event -> Player.serverLevel()
     * 2. Pokemon Entity -> Entity.level()
     * 3. Pokemon.getOwnerUUID() -> Player -> serverLevel()
     *
     * Gibt niemals null zurück - im schlimmsten Fall Overworld.
     */
    private static ResourceKey<Level> extractDimension(Object ev, Object pokemon, MinecraftServer server) {
        // 1) Versuche Player aus Event
        UUID playerId = extractPlayerId(ev);
        if (playerId != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                return player.serverLevel().dimension();
            }
        }

        // 2) Versuche Entity aus Pokemon zu holen (falls Pokemon in der Welt ist)
        Entity pokemonEntity = extractEntityFromPokemon(pokemon);
        if (pokemonEntity != null && pokemonEntity.level() instanceof ServerLevel sl) {
            return sl.dimension();
        }

        // 3) Versuche Owner-UUID aus Pokemon -> Player
        UUID ownerUuid = extractOwnerUuid(pokemon);
        if (ownerUuid != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                return owner.serverLevel().dimension();
            }
        }

        // 4) Fallback: Overworld
        return Level.OVERWORLD;
    }

    /* ------------------------------------------------------------------ */
    /* Reflection-Helper                                                  */
    /* ------------------------------------------------------------------ */

    private static UUID extractPlayerId(Object ev) {
        try {
            Method m = ev.getClass().getMethod("getPlayerId");
            Object o = m.invoke(ev);
            if (o instanceof UUID u) {
                return u;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object extractPokemon(Object ev) {
        try {
            Method m = ev.getClass().getMethod("getPokemon");
            return m.invoke(ev);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Versucht das Entity-Objekt aus einem Pokemon zu extrahieren.
     * Pokemon.getEntity() gibt das PokemonEntity zurück, falls es in der Welt gespawnt ist.
     */
    private static Entity extractEntityFromPokemon(Object pokemon) {
        if (pokemon == null) return null;

        String[] methodNames = {"getEntity", "entity"};
        for (String name : methodNames) {
            try {
                Method m = pokemon.getClass().getMethod(name);
                Object o = m.invoke(pokemon);
                if (o instanceof Entity e) {
                    return e;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /**
     * Versucht die Owner-UUID aus einem Pokemon zu extrahieren.
     */
    private static UUID extractOwnerUuid(Object pokemon) {
        if (pokemon == null) return null;

        String[] methodNames = {"getOwnerUUID", "getOwnerUuid", "getOwnerId", "ownerUUID"};
        for (String name : methodNames) {
            try {
                Method m = pokemon.getClass().getMethod(name);
                Object o = m.invoke(pokemon);
                if (o instanceof UUID u) {
                    return u;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}