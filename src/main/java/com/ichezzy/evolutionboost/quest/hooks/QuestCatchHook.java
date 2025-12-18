package com.ichezzy.evolutionboost.quest.hooks;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent;
import com.cobblemon.mod.common.api.events.pokemon.LevelUpEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.quest.QuestManager;
import com.ichezzy.evolutionboost.quest.QuestType;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Hook für Cobblemon Catch, Evolution und Level Events.
 */
public final class QuestCatchHook {
    private QuestCatchHook() {}

    public static void register() {
        // Pokemon gefangen
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, QuestCatchHook::onPokemonCaptured);

        // Pokemon entwickelt
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL, QuestCatchHook::onEvolutionComplete);

        // Pokemon Level-Up
        CobblemonEvents.LEVEL_UP.subscribe(Priority.NORMAL, QuestCatchHook::onLevelUp);

        EvolutionBoost.LOGGER.info("[quests] QuestCatchHook registered.");
    }

    /**
     * Wird aufgerufen wenn ein Pokemon gefangen wird.
     */
    private static Unit onPokemonCaptured(PokemonCapturedEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer player)) {
                return Unit.INSTANCE;
            }

            Pokemon pokemon = event.getPokemon();
            String species = pokemon.getSpecies().getName().toLowerCase();
            String primaryType = pokemon.getPrimaryType().getName().toLowerCase();
            String secondaryType = pokemon.getSecondaryType() != null
                    ? pokemon.getSecondaryType().getName().toLowerCase()
                    : null;
            int level = pokemon.getLevel();
            boolean shiny = pokemon.getShiny();
            List<String> aspects = new ArrayList<>(pokemon.getAspects());

            QuestManager.get().processProgress(player, QuestType.CATCH,
                    obj -> obj.matchesPokemon(species, primaryType, secondaryType, aspects, level, shiny),
                    1);

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in onPokemonCaptured: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }

    /**
     * Wird aufgerufen wenn ein Pokemon sich entwickelt.
     */
    private static Unit onEvolutionComplete(EvolutionCompleteEvent event) {
        try {
            Pokemon pokemon = event.getPokemon();

            // Finde den Besitzer
            ServerPlayer player = findOwner(pokemon);
            if (player == null) {
                return Unit.INSTANCE;
            }

            // Das neue Pokemon (nach Evolution)
            String species = pokemon.getSpecies().getName().toLowerCase();
            String primaryType = pokemon.getPrimaryType().getName().toLowerCase();
            String secondaryType = pokemon.getSecondaryType() != null
                    ? pokemon.getSecondaryType().getName().toLowerCase()
                    : null;
            int level = pokemon.getLevel();
            boolean shiny = pokemon.getShiny();
            List<String> aspects = new ArrayList<>(pokemon.getAspects());

            QuestManager.get().processProgress(player, QuestType.EVOLVE,
                    obj -> {
                        // Prüfe "species" für Ziel-Pokemon
                        List<String> speciesFilter = obj.getFilterList("species");
                        if (!speciesFilter.isEmpty()) {
                            return speciesFilter.stream().anyMatch(s -> s.equalsIgnoreCase(species));
                        }
                        // Wenn kein Filter, zählt jede Evolution
                        return true;
                    },
                    1);

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in onEvolutionComplete: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }

    /**
     * Wird aufgerufen wenn ein Pokemon ein Level aufsteigt.
     */
    private static Unit onLevelUp(LevelUpEvent event) {
        try {
            Pokemon pokemon = event.getPokemon();

            ServerPlayer player = findOwner(pokemon);
            if (player == null) {
                return Unit.INSTANCE;
            }

            String species = pokemon.getSpecies().getName().toLowerCase();
            String primaryType = pokemon.getPrimaryType().getName().toLowerCase();
            String secondaryType = pokemon.getSecondaryType() != null
                    ? pokemon.getSecondaryType().getName().toLowerCase()
                    : null;
            int newLevel = event.getNewLevel();
            boolean shiny = pokemon.getShiny();
            List<String> aspects = new ArrayList<>(pokemon.getAspects());

            QuestManager.get().processProgress(player, QuestType.LEVEL_UP,
                    obj -> {
                        // MinLevel prüfen (Ziel-Level erreicht?)
                        Integer minLevel = obj.getFilterInt("minLevel");
                        if (minLevel != null && newLevel < minLevel) {
                            return false;
                        }
                        return obj.matchesPokemon(species, primaryType, secondaryType, aspects, newLevel, shiny);
                    },
                    1);

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in onLevelUp: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }

    /**
     * Findet den Besitzer eines Pokemon.
     */
    private static ServerPlayer findOwner(Pokemon pokemon) {
        try {
            var storeCoordinates = pokemon.getStoreCoordinates().get();
            if (storeCoordinates == null) return null;

            var store = storeCoordinates.getStore();
            if (store == null) return null;

            var uuid = store.getUuid();
            if (uuid == null) return null;

            // Server holen und Spieler finden
            var server = net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
                    .SERVER_STARTING.invoker();
            // Alternativer Weg über gespeicherten Server
            return null; // TODO: Bessere Implementierung
        } catch (Exception e) {
            return null;
        }
    }
}
