package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Minimaler Kompat-Hook für Halloween-XP:
 * - Toggle: setHalloweenXpEnabled / isHalloweenXpEnabled
 * - Debug:  setDebug / isDebug
 * - applyXpMultiplier(...) kann von deinen Cobblemon-Hooks aufgerufen werden,
 *   um XP dynamisch (z. B. x2) zu skalieren, wenn der Spieler in event:halloween ist.
 */
public final class XpHook {
    private static volatile boolean halloweenXpEnabled = false;
    private static volatile boolean debug = false;

    private XpHook() {}

    // ---------------------------
    // Public API (vom Command genutzt)
    // ---------------------------
    public static void setHalloweenXpEnabled(boolean enabled) {
        halloweenXpEnabled = enabled;
        if (debug) {
            EvolutionBoost.LOGGER.info("[{}] Halloween XP enabled = {}", EvolutionBoost.MOD_ID, enabled);
        }
    }

    public static boolean isHalloweenXpEnabled() {
        return halloweenXpEnabled;
    }

    public static void setDebug(boolean dbg) {
        debug = dbg;
        EvolutionBoost.LOGGER.info("[{}] Halloween XP DEBUG = {}", EvolutionBoost.MOD_ID, dbg);
    }

    public static boolean isDebug() {
        return debug;
    }

    // ---------------------------
    // Helfer für XP-Skalierung (optional nutzen)
    // ---------------------------
    /**
     * Wende den Halloween-Multiplikator an (derzeit x2), falls:
     *  - Feature ist eingeschaltet UND
     *  - Spieler befindet sich in Dimension "event:halloween"
     */
    public static int applyXpMultiplier(ServerPlayer player, int baseXp) {
        if (!halloweenXpEnabled || player == null) return baseXp;

        ResourceLocation dim = player.serverLevel().dimension().location();
        boolean inHalloween = "event".equals(dim.getNamespace()) && "halloween".equals(dim.getPath());

        int result = inHalloween ? baseXp * 2 : baseXp;

        if (debug) {
            EvolutionBoost.LOGGER.info(
                    "[{}] XP hook -> player={}, dim={}, baseXp={}, result={}",
                    EvolutionBoost.MOD_ID,
                    player.getGameProfile().getName(),
                    dim,
                    baseXp,
                    result
            );
        }
        return result;
    }
}
