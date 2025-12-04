package com.ichezzy.evolutionboost.hud;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

/**
 * Synchronisiert NUR die dimensionalen Multiplikatoren zu den Clients,
 * damit dort ein kleines HUD angezeigt werden kann.
 *
 * Paket-ID: evolutionboost:dim_boost_hud
 */
public final class BoostHudSync {

    public static final ResourceLocation DIM_HUD_PACKET =
            ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "dim_boost_hud");

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

    /** Schickt pro BoostType genau EINEN Wert: den Dim-Multiplikator in der aktuellen Dimension des Spielers. */
    private static void sendSnapshot(BoostManager manager, ServerPlayer player) {
        ResourceKey<Level> dimKey = player.serverLevel().dimension();

        FriendlyByteBuf buf = PacketByteBufs.create();
        for (BoostType type : BoostType.values()) {
            double dimMult = manager.getDimensionMultiplier(type, dimKey);
            buf.writeDouble(dimMult);
        }

        ServerPlayNetworking.send(player, DIM_HUD_PACKET, buf);
    }
}
