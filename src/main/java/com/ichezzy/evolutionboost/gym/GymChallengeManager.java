package com.ichezzy.evolutionboost.gym;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wrapper-Klasse für Challenge-Funktionen.
 * Delegiert an GymManager.
 */
public final class GymChallengeManager {

    private GymChallengeManager() {}

    /**
     * Sendet eine Challenge-Anfrage.
     */
    public static void sendChallenge(ServerPlayer challenger, ServerPlayer leader, GymType gymType) {
        String error = GymManager.get().createChallenge(challenger, gymType);
        
        if (error != null) {
            challenger.sendSystemMessage(Component.literal("✗ " + error)
                    .withStyle(ChatFormatting.RED));
        } else {
            challenger.sendSystemMessage(Component.literal("✓ Challenge sent to " + leader.getName().getString())
                    .withStyle(ChatFormatting.GREEN));
            challenger.sendSystemMessage(Component.literal("  Waiting for response...")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Leader akzeptiert Challenge.
     * @return 1 bei Erfolg, 0 bei Fehler
     */
    public static int acceptChallenge(ServerPlayer leader) {
        String error = GymManager.get().acceptChallenge(leader);
        
        if (error != null) {
            leader.sendSystemMessage(Component.literal("✗ " + error)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        
        return 1;
    }

    /**
     * Leader lehnt Challenge ab.
     * @return 1 bei Erfolg, 0 bei Fehler
     */
    public static int declineChallenge(ServerPlayer leader) {
        String error = GymManager.get().declineChallenge(leader);
        
        if (error != null) {
            leader.sendSystemMessage(Component.literal("✗ " + error)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        
        leader.sendSystemMessage(Component.literal("✓ Challenge declined")
                .withStyle(ChatFormatting.YELLOW));
        return 1;
    }
}
