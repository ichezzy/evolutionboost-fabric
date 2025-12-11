package com.ichezzy.evolutionboost.hud;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Synchronisiert NUR die dimensionalen Multiplikatoren zu den Clients,
 * damit dort ein kleines HUD angezeigt werden kann.
 */
public final class BoostHudSync {

    private static int tickCounter = 0;

    private BoostHudSync() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BoostHudSync::onServerTick);
        EvolutionBoost.LOGGER.info("[EvolutionBoost] BoostHudSync (dim-only) initialised.");
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        // alle 40 Ticks ~ 2 Sekunden
        if (tickCounter % 40 != 0) return;

        BoostManager manager = BoostManager.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSnapshot(manager, player);
        }
    }

    /**
     * Schickt pro BoostType genau EINEN Wert:
     * den Dim-Multiplikator in der aktuellen Dimension des Spielers.
     */
    private static void sendSnapshot(BoostManager manager, ServerPlayer player) {
        ResourceKey<Level> dimKey = player.serverLevel().dimension();

        double[] dims = new double[BoostType.values().length];
        for (int i = 0; i < BoostType.values().length; i++) {
            BoostType type = BoostType.values()[i];
            dims[i] = manager.getDimensionMultiplier(type, dimKey);
        }

        DimBoostHudPayload payload = new DimBoostHudPayload(dims);
        // NEUE API: nur 2 Argumente (Player + Payload)
        ServerPlayNetworking.send(player, payload);
    }
}
