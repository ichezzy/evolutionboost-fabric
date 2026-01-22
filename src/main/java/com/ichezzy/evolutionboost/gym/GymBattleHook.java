package com.ichezzy.evolutionboost.gym;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.ichezzy.evolutionboost.EvolutionBoost;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hook für Cobblemon Battle Events.
 * Trackt Gym-Battles und vergibt Rewards bei Challenger-Sieg.
 * 
 * Nur Battles die über /eb gym challenge gestartet wurden zählen!
 */
public final class GymBattleHook {

    private GymBattleHook() {}

    public static void register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.NORMAL, GymBattleHook::onBattleVictory);
        EvolutionBoost.LOGGER.info("[gym] GymBattleHook registered");
    }

    private static Unit onBattleVictory(BattleVictoryEvent event) {
        try {
            PokemonBattle battle = event.getBattle();
            
            // Finde die beiden Spieler
            ServerPlayer player1 = null;
            ServerPlayer player2 = null;
            int playerCount = 0;
            
            for (BattleActor actor : battle.getActors()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    ServerPlayer player = playerActor.getEntity();
                    if (player != null) {
                        playerCount++;
                        if (player1 == null) {
                            player1 = player;
                        } else {
                            player2 = player;
                        }
                    }
                }
            }

            // Nur 1v1 PvP Battles
            if (playerCount != 2 || player1 == null || player2 == null) {
                return Unit.INSTANCE;
            }

            // Prüfe ob einer der Spieler ein aktives Gym-Battle hat
            GymManager mgr = GymManager.get();
            GymManager.ActiveBattle activeBattle = null;

            // Suche nach aktivem Battle mit diesen beiden Spielern
            activeBattle = mgr.findActiveBattle(player1.getUUID(), player2.getUUID());
            
            if (activeBattle == null) {
                return Unit.INSTANCE; // Kein getracktes Gym-Battle
            }

            // Wer hat gewonnen?
            boolean challengerWon = false;
            for (BattleActor winner : event.getWinners()) {
                if (winner instanceof PlayerBattleActor pa) {
                    ServerPlayer winnerPlayer = pa.getEntity();
                    if (winnerPlayer != null && 
                        winnerPlayer.getUUID().equals(activeBattle.challengerUUID)) {
                        challengerWon = true;
                        break;
                    }
                }
            }

            // Spieler für Nachrichten
            ServerPlayer challenger = mgr.getServer().getPlayerList().getPlayer(activeBattle.challengerUUID);
            ServerPlayer leader = mgr.getServer().getPlayerList().getPlayer(activeBattle.leaderUUID);

            // Battle-Ergebnis
            GymData.BattleResult result = challengerWon 
                    ? GymData.BattleResult.CHALLENGER_WIN 
                    : GymData.BattleResult.LEADER_WIN;

            // Battle abschließen
            mgr.finishBattle(activeBattle, result, challenger, leader);

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[gym] Error in onBattleVictory: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }
}
