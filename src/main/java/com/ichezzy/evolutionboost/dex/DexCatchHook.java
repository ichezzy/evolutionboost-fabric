package com.ichezzy.evolutionboost.dex;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.PokedexDataChangedEvent;
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.DebugConfig;
import kotlin.Unit;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hook für Cobblemon Pokédex Events.
 * Registriert Pokédex-Updates (Starter, Fänge, Entwicklungen, Trades - alles!).
 */
public final class DexCatchHook {
    private DexCatchHook() {}

    public static void register() {
        // POKEDEX_DATA_CHANGED_POST wird gefeuert wenn:
        // - Ein Pokémon gefangen wird
        // - Ein Starter gewählt wird
        // - Ein Pokémon durch Trade erhalten wird
        // - Ein Pokémon sich entwickelt
        // - Basically jede Änderung am Pokédex
        CobblemonEvents.POKEDEX_DATA_CHANGED_POST.subscribe(Priority.NORMAL, DexCatchHook::onPokedexChanged);
        EvolutionBoost.LOGGER.info("[dex] DexCatchHook registered (using POKEDEX_DATA_CHANGED_POST).");
    }

    private static Unit onPokedexChanged(PokedexDataChangedEvent.Post event) {
        try {
            // Nur bei CAUGHT-Status reagieren (nicht bei ENCOUNTERED)
            if (event.getKnowledge() != PokedexEntryProgress.CAUGHT) {
                return Unit.INSTANCE;
            }

            // Spieler holen
            var server = Cobblemon.INSTANCE.getImplementation().server();
            if (server == null) return Unit.INSTANCE;

            ServerPlayer player = server.getPlayerList().getPlayer(event.getPlayerUUID());
            if (player == null) return Unit.INSTANCE;

            // Species-Name aus dem Record
            String species = event.getRecord().getSpeciesDexRecord().getId().getPath();

            if (DebugConfig.get().debugDex) {
                EvolutionBoost.LOGGER.info("[dex][debug] {} registered in Pokédex: {}",
                        player.getGameProfile().getName(), species);
            }

            // Milestone-Checks durchführen
            DexDataManager.checkMilestonesAndNotify(player);

        } catch (Exception e) {
            EvolutionBoost.LOGGER.debug("[dex] Error in onPokedexChanged: {}", e.getMessage());
        }

        return Unit.INSTANCE;
    }
}
