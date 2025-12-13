package com.ichezzy.evolutionboost.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.entity.SpawnEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Shiny-Hook:
 * - hängt direkt an CobblemonEvents.POKEMON_ENTITY_SPAWN
 * - liest den SHINY-Boost (GLOBAL × DIMENSION) aus BoostManager
 * - forciert zusätzliche Shiny-Rolls anhand der shinyBaseOdds-Config
 */
public final class ShinyHook {
    private ShinyHook() {}

    /**
     * Alte Signatur beibehalten, damit HooksRegistrar unverändert bleibt.
     * clsEvents/priority werden hier nicht benötigt.
     */
    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        register(server);
    }

    /** Eigentliche Registrierung am Cobblemon-Event. */
    private static void register(MinecraftServer server) {
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(
                Priority.NORMAL,
                new Function1<SpawnEvent<PokemonEntity>, Unit>() {
                    @Override
                    public Unit invoke(SpawnEvent<PokemonEntity> ev) {
                        try {
                            handleSpawn(server, ev);
                        } catch (Throwable t) {
                            EvolutionBoost.LOGGER.warn(
                                    "[compat][shiny] error in spawn handler: {}",
                                    t.toString()
                            );
                        }
                        return Unit.INSTANCE;
                    }
                }
        );

        EvolutionBoost.LOGGER.info("[compat][shiny] POKEMON_ENTITY_SPAWN hook registered.");
    }

    /** Wird bei jedem Pokémon-Spawn aufgerufen. */
    private static void handleSpawn(MinecraftServer server, SpawnEvent<PokemonEntity> ev) {
        PokemonEntity entity = ev.getEntity();
        if (entity == null) return;

        // nur Serverseite
        if (entity.level().isClientSide()) return;

        ResourceKey<Level> dimKey = entity.level().dimension();

        // NEU: GLOBAL × DIMENSION statt nur GLOBAL
        double mult = BoostManager.get(server).getMultiplierFor(BoostType.SHINY, null, dimKey);
        if (mult <= 1.0) {
            return; // kein Extra-Boost aktiv
        }

        Pokemon pokemon = getPokemon(entity);
        if (pokemon == null) {
            return;
        }

        if (isCurrentlyShiny(pokemon)) {
            // bereits shiny, nichts tun
            return;
        }

        // --- Formel basierend auf shinyBaseOdds aus EvolutionBoostConfig ---

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
        int baseOdds = cfg.shinyBaseOdds <= 0 ? 8192 : cfg.shinyBaseOdds;

        // Basis-Shinychance (z.B. 1/8192)
        double pb = 1.0 / (double) baseOdds;

        // Extra-Chance, um von pb auf mult * pb zu kommen:
        // EndChance ~= pb + (1 - pb) * extraChance  ≈ mult * pb
        // => extraChance ~ (mult - 1) * pb   (für pb << 1)
        double extraChance = (mult - 1.0) * pb;

        // Begrenzen, damit es nicht völlig eskaliert
        if (extraChance <= 0.0) return;
        if (extraChance > 0.95) extraChance = 0.95;

        double roll = entity.level().getRandom().nextDouble();
        if (roll < extraChance) {
            setShiny(pokemon, true);
            EvolutionBoost.LOGGER.debug(
                    "[compat][shiny] Force-shiny applied (mult={}, dim={}, extraChance={})",
                    mult, dimKey.location(), extraChance
            );
        }
    }

    /* --------- Reflection-Helper auf Cobblemon-Klassen --------- */

    private static Pokemon getPokemon(PokemonEntity entity) {
        try {
            Method m = entity.getClass().getMethod("getPokemon");
            Object o = m.invoke(entity);
            if (o instanceof Pokemon p) return p;
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Versucht über Reflection herauszufinden, ob das Pokémon bereits shiny ist.
     * Probiert typische Methoden wie isShiny() / getShiny().
     */
    private static boolean isCurrentlyShiny(Pokemon pokemon) {
        try {
            // 1) isShiny(): Boolean
            try {
                Method m = pokemon.getClass().getMethod("isShiny");
                Object o = m.invoke(pokemon);
                if (o instanceof Boolean b) return b;
            } catch (NoSuchMethodException ignored) {}

            // 2) getShiny(): Boolean
            try {
                Method m = pokemon.getClass().getMethod("getShiny");
                Object o = m.invoke(pokemon);
                if (o instanceof Boolean b) return b;
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Setzt shiny per Reflection (z.B. setShiny(boolean)).
     */
    private static void setShiny(Pokemon pokemon, boolean shiny) {
        try {
            Method m = pokemon.getClass().getMethod("setShiny", boolean.class);
            m.invoke(pokemon, shiny);
        } catch (Throwable ignored) {
            // Wenn die Methode in einer zukünftigen Version anders heißt,
            // verhindern wir damit zumindest einen Crash.
        }
    }
}
