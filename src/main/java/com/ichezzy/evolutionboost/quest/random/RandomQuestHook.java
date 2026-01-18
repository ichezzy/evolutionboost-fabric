package com.ichezzy.evolutionboost.quest.random;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.ichezzy.evolutionboost.EvolutionBoost;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Hook für Cobblemon Events um Random Quest Fortschritt zu tracken.
 */
public class RandomQuestHook {

    /** Legendäre Pokémon (vereinfachte Liste - kann erweitert werden) */
    private static final Set<String> LEGENDARIES = Set.of(
            "articuno", "zapdos", "moltres", "mewtwo", "mew",
            "raikou", "entei", "suicune", "lugia", "ho-oh", "celebi",
            "regirock", "regice", "registeel", "latias", "latios",
            "kyogre", "groudon", "rayquaza", "jirachi", "deoxys",
            "uxie", "mesprit", "azelf", "dialga", "palkia", "giratina",
            "heatran", "regigigas", "cresselia", "phione", "manaphy",
            "darkrai", "shaymin", "arceus",
            "victini", "cobalion", "terrakion", "virizion", "tornadus",
            "thundurus", "landorus", "reshiram", "zekrom", "kyurem",
            "keldeo", "meloetta", "genesect",
            "xerneas", "yveltal", "zygarde", "diancie", "hoopa", "volcanion",
            "typenull", "silvally", "tapukoko", "tapulele", "tapubulu", "tapufini",
            "cosmog", "cosmoem", "solgaleo", "lunala", "necrozma",
            "magearna", "marshadow", "zeraora",
            "zacian", "zamazenta", "eternatus", "kubfu", "urshifu",
            "regieleki", "regidrago", "glastrier", "spectrier", "calyrex",
            "enamorus", "koraidon", "miraidon", "tinglu", "chienpao",
            "wochien", "chiyu", "ogerpon", "terapagos", "pecharunt"
    );

    public static void register() {
        // ==================== Catch Events ====================
        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL, event -> {
            try {
                ServerPlayer player = event.getPlayer();
                Pokemon pokemon = event.getPokemon();

                if (player == null || pokemon == null) return Unit.INSTANCE;

                UUID playerId = player.getUUID();
                RandomQuestManager manager = RandomQuestManager.get();

                // CATCH_ANY
                manager.addProgress(playerId, RandomQuestObjectiveType.CATCH_ANY, 1, null);

                // CATCH_TYPE - für jeden Typ des Pokémon
                for (ElementalType type : pokemon.getTypes()) {
                    manager.addProgress(playerId, RandomQuestObjectiveType.CATCH_TYPE, 1, type.getName().toLowerCase());
                }

                // CATCH_SHINY
                if (pokemon.getShiny()) {
                    manager.addProgress(playerId, RandomQuestObjectiveType.CATCH_SHINY, 1, null);
                }

                // CATCH_LEGENDARY
                String speciesName = pokemon.getSpecies().getName().toLowerCase();
                if (LEGENDARIES.contains(speciesName)) {
                    manager.addProgress(playerId, RandomQuestObjectiveType.CATCH_LEGENDARY, 1, null);
                }

                // CATCH_HA - Hidden Ability
                if (hasHiddenAbility(pokemon)) {
                    manager.addProgress(playerId, RandomQuestObjectiveType.CATCH_HA, 1, null);
                }

                // CATCH_NATURE
                if (pokemon.getNature() != null) {
                    String natureName = pokemon.getNature().getName().getPath().toLowerCase();
                    manager.addProgress(playerId, RandomQuestObjectiveType.CATCH_NATURE, 1, natureName);
                }

                EvolutionBoost.LOGGER.debug("[random-quest] Player {} caught {} (Types: {})",
                        player.getName().getString(), speciesName, pokemon.getTypes());

            } catch (Exception e) {
                EvolutionBoost.LOGGER.debug("[random-quest] Error in catch event: {}", e.getMessage());
            }

            return Unit.INSTANCE;
        });

        // ==================== Battle Victory Events ====================
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, event -> {
            try {
                // Alle Gewinner durchgehen
                for (var winner : event.getWinners()) {
                    if (!(winner instanceof PlayerBattleActor playerActor)) {
                        continue;
                    }

                    ServerPlayer player = playerActor.getEntity();
                    if (player == null) continue;

                    UUID playerId = player.getUUID();
                    RandomQuestManager manager = RandomQuestManager.get();

                    // WIN_BATTLE
                    manager.addProgress(playerId, RandomQuestObjectiveType.WIN_BATTLE, 1, null);

                    // DEFEAT_WILD / DEFEAT_TYPE - basierend auf besiegten Pokémon
                    for (var opponent : event.getLosers()) {
                        // Nur wilde Pokémon zählen (kein PlayerBattleActor)
                        if (opponent instanceof PlayerBattleActor) continue;

                        for (var battlePokemon : opponent.getPokemonList()) {
                            Pokemon pokemon = battlePokemon.getOriginalPokemon();
                            if (pokemon == null) continue;

                            manager.addProgress(playerId, RandomQuestObjectiveType.DEFEAT_WILD, 1, null);

                            for (ElementalType type : pokemon.getTypes()) {
                                manager.addProgress(playerId, RandomQuestObjectiveType.DEFEAT_TYPE, 1,
                                        type.getName().toLowerCase());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.debug("[random-quest] Error in battle victory event: {}", e.getMessage());
            }

            return Unit.INSTANCE;
        });

        // ==================== Evolution Events ====================
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(Priority.NORMAL, event -> {
            try {
                Pokemon pokemon = event.getPokemon();
                ServerPlayer player = findOwner(pokemon);
                if (player != null) {
                    RandomQuestManager.get().addProgress(player.getUUID(), RandomQuestObjectiveType.EVOLVE, 1, null);
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.debug("[random-quest] Error in evolution event: {}", e.getMessage());
            }
            return Unit.INSTANCE;
        });

        // ==================== Level Up Events ====================
        CobblemonEvents.LEVEL_UP_EVENT.subscribe(Priority.NORMAL, event -> {
            try {
                Pokemon pokemon = event.getPokemon();
                ServerPlayer player = findOwner(pokemon);
                if (player != null) {
                    // Jedes Level-Up zählt
                    int levelsGained = event.getNewLevel() - event.getOldLevel();
                    RandomQuestManager.get().addProgress(player.getUUID(), RandomQuestObjectiveType.LEVEL_UP,
                            levelsGained, null);
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.debug("[random-quest] Error in level up event: {}", e.getMessage());
            }
            return Unit.INSTANCE;
        });

        // ==================== XP Events ====================
        CobblemonEvents.EXPERIENCE_GAINED_EVENT_PRE.subscribe(Priority.NORMAL, event -> {
            try {
                Pokemon pokemon = event.getPokemon();
                ServerPlayer player = findOwner(pokemon);
                if (player != null) {
                    int xpGained = event.getExperience();
                    RandomQuestManager.get().addProgress(player.getUUID(), RandomQuestObjectiveType.GAIN_POKEMON_XP,
                            xpGained, null);
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.debug("[random-quest] Error in XP event: {}", e.getMessage());
            }
            return Unit.INSTANCE;
        });

        // Hinweis: HATCH_EGG ist derzeit nicht implementiert, da Cobblemon 1.6.1 kein Hatch-Event hat.
        // Kann später über POKEMON_GAINED + Level-1-Check implementiert werden.

        EvolutionBoost.LOGGER.info("[random-quest] Cobblemon hooks registered");
    }

    /**
     * Prüft ob ein Pokémon seine Hidden Ability hat.
     */
    private static boolean hasHiddenAbility(Pokemon pokemon) {
        try {
            if (pokemon.getAbility() == null) return false;

            var form = pokemon.getForm();
            var abilities = form.getAbilities();

            // Die Ability an Index 2 (oder höher) ist typischerweise die Hidden Ability
            // Cobblemon nutzt priority-basiertes System
            String currentAbilityName = pokemon.getAbility().getName().toLowerCase();

            // Hole alle möglichen Abilities und prüfe ob die aktuelle die "seltene" ist
            var possibleAbilities = abilities.getMapping();
            for (var entry : possibleAbilities.entrySet()) {
                var priority = entry.getKey();
                var abilityPool = entry.getValue();

                // Priority 2+ sind typischerweise Hidden Abilities in Cobblemon
                if (priority.ordinal() >= 2) {
                    for (var potentialAbility : abilityPool) {
                        AbilityTemplate template = potentialAbility.getTemplate();
                        if (template != null && template.getName().toLowerCase().equals(currentAbilityName)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[random-quest] Error checking hidden ability: {}", e.getMessage());
            return false;
        }
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

            var server = com.cobblemon.mod.common.Cobblemon.INSTANCE.getImplementation().server();
            if (server == null) return null;

            return server.getPlayerList().getPlayer(uuid);
        } catch (Exception e) {
            return null;
        }
    }
}
