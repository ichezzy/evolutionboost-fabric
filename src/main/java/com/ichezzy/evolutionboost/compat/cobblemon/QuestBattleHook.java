package com.ichezzy.evolutionboost.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.quest.QuestManager;
import com.ichezzy.evolutionboost.quest.QuestType;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Hook für Cobblemon Battle Events.
 * Trackt DEFEAT und BATTLE Quest-Objectives.
 */
public final class QuestBattleHook {
    private QuestBattleHook() {}

    public static void register() {
        // Battle Fainted Event - für DEFEAT Objectives
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL, QuestBattleHook::onBattleFainted);

        // Battle Victory Event - für BATTLE Objectives (Battle abgeschlossen)
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, QuestBattleHook::onBattleVictory);

        EvolutionBoost.LOGGER.info("[quests] QuestBattleHook registered.");
    }

    /**
     * Wird aufgerufen wenn ein Pokemon im Battle besiegt wird.
     */
    private static Unit onBattleFainted(BattleFaintedEvent event) {
        try {
            PokemonBattle battle = event.getBattle();
            BattlePokemon faintedBattlePokemon = event.getKilled();

            // Pokemon-Daten extrahieren
            Pokemon faintedPokemon = faintedBattlePokemon.getOriginalPokemon();
            if (faintedPokemon == null) {
                return Unit.INSTANCE;
            }

            // Finde den Actor zu dem das besiegte Pokemon gehört
            BattleActor faintedActor = faintedBattlePokemon.getActor();

            // Finde alle Spieler-Actors die NICHT der besiegte Actor sind (= die Gegner/Killer)
            for (BattleActor actor : battle.getActors()) {
                if (actor == faintedActor) continue; // Skip den Actor dessen Pokemon besiegt wurde

                if (actor instanceof PlayerBattleActor playerActor) {
                    ServerPlayer player = playerActor.getEntity();
                    if (player == null) continue;

                    // Pokemon-Info extrahieren
                    String species = faintedPokemon.getSpecies().getName().toLowerCase();
                    String primaryType = faintedPokemon.getPrimaryType().getName().toLowerCase();
                    String secondaryType = faintedPokemon.getSecondaryType() != null
                            ? faintedPokemon.getSecondaryType().getName().toLowerCase()
                            : null;
                    int level = faintedPokemon.getLevel();
                    boolean shiny = faintedPokemon.getShiny();
                    List<String> aspects = new ArrayList<>(faintedPokemon.getAspects());

                    // Fortschritt für DEFEAT-Objectives
                    QuestManager.get().processProgress(player, QuestType.DEFEAT,
                            obj -> obj.matchesPokemon(species, primaryType, secondaryType, aspects, level, shiny),
                            1);
                }
            }

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in onBattleFainted: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }

    /**
     * Wird aufgerufen wenn ein Battle gewonnen wird.
     */
    private static Unit onBattleVictory(BattleVictoryEvent event) {
        try {
            PokemonBattle battle = event.getBattle();

            // Alle Gewinner durchgehen
            for (BattleActor winner : event.getWinners()) {
                if (!(winner instanceof PlayerBattleActor playerActor)) {
                    continue;
                }

                ServerPlayer player = playerActor.getEntity();
                if (player == null) continue;

                // Alle gegnerischen Pokemon im Battle
                for (BattleActor opponent : battle.getActors()) {
                    if (opponent == winner) continue;

                    for (BattlePokemon battlePokemon : opponent.getPokemonList()) {
                        Pokemon pokemon = battlePokemon.getOriginalPokemon();
                        if (pokemon == null) continue;

                        String species = pokemon.getSpecies().getName().toLowerCase();
                        String primaryType = pokemon.getPrimaryType().getName().toLowerCase();
                        String secondaryType = pokemon.getSecondaryType() != null
                                ? pokemon.getSecondaryType().getName().toLowerCase()
                                : null;
                        int level = pokemon.getLevel();
                        boolean shiny = pokemon.getShiny();
                        List<String> aspects = new ArrayList<>(pokemon.getAspects());

                        // Fortschritt für BATTLE-Objectives (battle = jedes Pokemon im Battle)
                        QuestManager.get().processProgress(player, QuestType.BATTLE,
                                obj -> obj.matchesPokemon(species, primaryType, secondaryType, aspects, level, shiny),
                                1);
                    }
                }
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[quests] Error in onBattleVictory: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }
}
