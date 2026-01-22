package com.ichezzy.evolutionboost.gym;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.util.*;

/**
 * Manager für das Gym-System.
 * 
 * Verantwortlich für:
 * - Leader-Verwaltung (setzen, entfernen)
 * - Challenge-System
 * - Battle-Tracking
 * - Reward-Vergabe
 */
public final class GymManager {

    private static GymManager INSTANCE;
    private MinecraftServer server;

    // Pending Challenges: challengerUUID -> PendingChallenge
    private final Map<UUID, PendingChallenge> pendingChallenges = new HashMap<>();

    // Active Battles: battleId -> ActiveBattle
    private final Map<UUID, ActiveBattle> activeBattles = new HashMap<>();

    private GymManager() {}

    public static GymManager get() {
        if (INSTANCE == null) {
            INSTANCE = new GymManager();
        }
        return INSTANCE;
    }

    public void init(MinecraftServer server) {
        this.server = server;
        GymConfig.get(); // Config laden
        GymData.init(server); // Daten laden
        EvolutionBoost.LOGGER.info("[gym] GymManager initialized");
    }

    public void shutdown() {
        GymData.save();
        pendingChallenges.clear();
        activeBattles.clear();
    }

    public MinecraftServer getServer() {
        return server;
    }

    // ==================== Leader Management ====================

    /**
     * Setzt einen neuen Leader für ein Gym.
     */
    public boolean setLeader(GymType gymType, ServerPlayer player, String setBy) {
        GymConfig cfg = GymConfig.get();
        GymConfig.GymEntry gym = cfg.getGym(gymType);
        if (gym == null) return false;

        String oldLeader = gym.currentLeader;
        String oldLeaderUUID = gym.currentLeaderUUID;

        // Neuen Leader setzen
        gym.currentLeader = player.getGameProfile().getName();
        gym.currentLeaderUUID = player.getStringUUID();
        gym.leaderStartDate = Instant.now().toString();

        GymConfig.save();

        // Logging
        if (oldLeader != null && !oldLeader.isEmpty()) {
            GymLogManager.logLeaderChanged(gymType, 
                    oldLeader, oldLeaderUUID,
                    gym.currentLeader, gym.currentLeaderUUID,
                    setBy);
        } else {
            GymLogManager.logLeaderSet(gymType, gym.currentLeader, gym.currentLeaderUUID, setBy);
        }

        EvolutionBoost.LOGGER.info("[gym] {} is now the {} Leader (set by {})", 
                player.getGameProfile().getName(), gymType.getDisplayName(), setBy);

        return true;
    }

    /**
     * Setzt einen neuen Leader für ein Gym (by Name, offline).
     */
    public boolean setLeaderByName(GymType gymType, String playerName, String playerUUID, String setBy) {
        GymConfig cfg = GymConfig.get();
        GymConfig.GymEntry gym = cfg.getGym(gymType);
        if (gym == null) return false;

        String oldLeader = gym.currentLeader;
        String oldLeaderUUID = gym.currentLeaderUUID;

        gym.currentLeader = playerName;
        gym.currentLeaderUUID = playerUUID;
        gym.leaderStartDate = Instant.now().toString();

        GymConfig.save();

        if (oldLeader != null && !oldLeader.isEmpty()) {
            GymLogManager.logLeaderChanged(gymType, 
                    oldLeader, oldLeaderUUID,
                    gym.currentLeader, gym.currentLeaderUUID,
                    setBy);
        } else {
            GymLogManager.logLeaderSet(gymType, gym.currentLeader, gym.currentLeaderUUID, setBy);
        }

        return true;
    }

    /**
     * Entfernt den aktuellen Leader eines Gyms.
     */
    public boolean removeLeader(GymType gymType, String removedBy, String reason) {
        GymConfig cfg = GymConfig.get();
        GymConfig.GymEntry gym = cfg.getGym(gymType);
        if (gym == null || gym.currentLeader == null) return false;

        String oldLeader = gym.currentLeader;
        String oldLeaderUUID = gym.currentLeaderUUID;

        gym.currentLeader = null;
        gym.currentLeaderUUID = null;
        gym.leaderStartDate = null;

        GymConfig.save();

        GymLogManager.logLeaderRemoved(gymType, oldLeader, oldLeaderUUID, removedBy, reason);

        EvolutionBoost.LOGGER.info("[gym] {} Leader {} removed by {} ({})", 
                gymType.getDisplayName(), oldLeader, removedBy, reason);

        return true;
    }

    // ==================== Gym Information ====================

    public String getLeaderName(GymType gymType) {
        GymConfig.GymEntry gym = GymConfig.get().getGym(gymType);
        return gym != null ? gym.currentLeader : null;
    }

    public String getLeaderUUID(GymType gymType) {
        GymConfig.GymEntry gym = GymConfig.get().getGym(gymType);
        return gym != null ? gym.currentLeaderUUID : null;
    }

    public boolean isLeader(ServerPlayer player, GymType gymType) {
        String leaderUUID = getLeaderUUID(gymType);
        return leaderUUID != null && leaderUUID.equals(player.getStringUUID());
    }

    public boolean isAnyLeader(ServerPlayer player) {
        String uuid = player.getStringUUID();
        for (GymType type : GymType.values()) {
            String leaderUUID = getLeaderUUID(type);
            if (leaderUUID != null && leaderUUID.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    public GymType getLeaderGym(ServerPlayer player) {
        String uuid = player.getStringUUID();
        for (GymType type : GymType.values()) {
            String leaderUUID = getLeaderUUID(type);
            if (leaderUUID != null && leaderUUID.equals(uuid)) {
                return type;
            }
        }
        return null;
    }

    public List<GymType> getActiveGyms() {
        List<GymType> active = new ArrayList<>();
        for (GymType type : GymType.values()) {
            GymConfig.GymEntry gym = GymConfig.get().getGym(type);
            if (gym != null && gym.enabled && gym.currentLeader != null) {
                active.add(type);
            }
        }
        return active;
    }

    public ServerPlayer getLeaderPlayer(GymType gymType) {
        if (server == null) return null;
        String uuid = getLeaderUUID(gymType);
        if (uuid == null) return null;
        
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Challenge System ====================

    /**
     * Erstellt eine neue Challenge-Anfrage.
     * @return Fehlermeldung oder null bei Erfolg
     */
    public String createChallenge(ServerPlayer challenger, GymType gymType) {
        GymConfig.GymEntry gym = GymConfig.get().getGym(gymType);
        
        // Prüfungen
        if (gym == null || !gym.enabled) {
            return "This gym is not available.";
        }
        if (gym.currentLeader == null) {
            return "This gym has no leader.";
        }
        if (challenger.getStringUUID().equals(gym.currentLeaderUUID)) {
            return "You cannot challenge your own gym!";
        }
        
        // Leader online?
        ServerPlayer leader = getLeaderPlayer(gymType);
        if (leader == null) {
            return "The gym leader is not online.";
        }

        // Proximity Check
        double distance = challenger.distanceTo(leader);
        if (distance > GymConfig.get().challengeRadius) {
            return "You must be within " + GymConfig.get().challengeRadius + 
                   " blocks of the leader. (Currently: " + (int)distance + " blocks)";
        }

        // Hat Challenger bereits eine pending Challenge?
        if (pendingChallenges.containsKey(challenger.getUUID())) {
            return "You already have a pending challenge. Wait for a response or timeout.";
        }

        // Hat Leader bereits eine pending Challenge?
        if (leaderHasPendingChallenge(leader.getUUID())) {
            return "The leader already has a pending challenge.";
        }

        // Challenge erstellen
        PendingChallenge challenge = new PendingChallenge(
                challenger.getUUID(), challenger.getGameProfile().getName(),
                leader.getUUID(), leader.getGameProfile().getName(),
                gymType
        );
        pendingChallenges.put(challenger.getUUID(), challenge);

        // Nachrichten senden
        leader.sendSystemMessage(Component.literal("⚔ ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(challenger.getGameProfile().getName())
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" challenges you to a ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(gymType.getDisplayName() + " Gym")
                        .withStyle(gymType.getColor()))
                .append(Component.literal(" battle!")
                        .withStyle(ChatFormatting.WHITE)));
        
        leader.sendSystemMessage(Component.literal("  Use ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/eb gym accept")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" or ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/eb gym decline")
                        .withStyle(ChatFormatting.RED)));

        return null; // Erfolg
    }

    /**
     * Leader akzeptiert die Challenge.
     * @return Fehlermeldung oder null bei Erfolg
     */
    public String acceptChallenge(ServerPlayer leader) {
        PendingChallenge challenge = getPendingChallengeForLeader(leader.getUUID());
        
        if (challenge == null) {
            return "You have no pending challenges.";
        }
        if (challenge.isExpired()) {
            pendingChallenges.remove(challenge.challengerUUID);
            return "The challenge has expired.";
        }

        // Challenger noch online?
        ServerPlayer challenger = server.getPlayerList().getPlayer(challenge.challengerUUID);
        if (challenger == null) {
            pendingChallenges.remove(challenge.challengerUUID);
            return "The challenger is no longer online.";
        }

        // Challenge entfernen
        pendingChallenges.remove(challenge.challengerUUID);

        // Battle starten
        return startGymBattle(challenger, leader, challenge.gymType);
    }

    /**
     * Leader lehnt die Challenge ab.
     */
    public String declineChallenge(ServerPlayer leader) {
        PendingChallenge challenge = getPendingChallengeForLeader(leader.getUUID());
        
        if (challenge == null) {
            return "You have no pending challenges.";
        }

        pendingChallenges.remove(challenge.challengerUUID);

        // Challenger benachrichtigen
        ServerPlayer challenger = server.getPlayerList().getPlayer(challenge.challengerUUID);
        if (challenger != null) {
            challenger.sendSystemMessage(Component.literal("✗ ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(leader.getGameProfile().getName())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" declined your challenge.")
                            .withStyle(ChatFormatting.RED)));
        }

        return null;
    }

    /**
     * Startet ein Cobblemon Battle zwischen Challenger und Leader.
     */
    private String startGymBattle(ServerPlayer challenger, ServerPlayer leader, GymType gymType) {
        try {
            // Cobblemon Battle via Command starten
            // Format: /pokebattle <player1> <player2>
            String command = "pokebattle " + challenger.getGameProfile().getName() + 
                           " " + leader.getGameProfile().getName();
            
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), 
                    command
            );

            // ActiveBattle tracken (wir bekommen die Battle-ID vom Event)
            // Temporär mit placeholder UUID - wird vom Hook aktualisiert
            UUID tempBattleId = UUID.randomUUID();
            ActiveBattle activeBattle = new ActiveBattle(
                    tempBattleId,
                    challenger.getUUID(), challenger.getGameProfile().getName(),
                    leader.getUUID(), leader.getGameProfile().getName(),
                    gymType
            );
            activeBattles.put(tempBattleId, activeBattle);

            // Nachrichten
            Component battleStart = Component.literal("⚔ ")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(gymType.getDisplayName() + " Gym Battle")
                            .withStyle(gymType.getColor(), ChatFormatting.BOLD))
                    .append(Component.literal(" started!")
                            .withStyle(ChatFormatting.WHITE));
            
            challenger.sendSystemMessage(battleStart);
            leader.sendSystemMessage(battleStart);

            EvolutionBoost.LOGGER.info("[gym] Battle started: {} vs {} ({})", 
                    challenger.getGameProfile().getName(),
                    leader.getGameProfile().getName(),
                    gymType.getDisplayName());

            return null;
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[gym] Failed to start battle: {}", e.getMessage());
            return "Failed to start battle: " + e.getMessage();
        }
    }

    // ==================== Battle Tracking ====================

    /**
     * Registriert ein aktives Battle (aufgerufen wenn Cobblemon Battle startet).
     */
    public void registerActiveBattle(UUID battleId, UUID challengerUUID, UUID leaderUUID, GymType gymType) {
        // Finde das temporäre ActiveBattle und update die Battle-ID
        for (Map.Entry<UUID, ActiveBattle> entry : new HashMap<>(activeBattles).entrySet()) {
            ActiveBattle ab = entry.getValue();
            if (ab.challengerUUID.equals(challengerUUID) && ab.leaderUUID.equals(leaderUUID)) {
                activeBattles.remove(entry.getKey());
                ab = new ActiveBattle(battleId, ab.challengerUUID, ab.challengerName,
                        ab.leaderUUID, ab.leaderName, ab.gymType);
                activeBattles.put(battleId, ab);
                return;
            }
        }
    }

    public ActiveBattle getActiveBattle(UUID battleId) {
        return activeBattles.get(battleId);
    }

    /**
     * Findet ein aktives Battle zwischen zwei Spielern.
     */
    public ActiveBattle findActiveBattle(UUID player1, UUID player2) {
        for (ActiveBattle battle : activeBattles.values()) {
            if ((battle.challengerUUID.equals(player1) && battle.leaderUUID.equals(player2)) ||
                (battle.challengerUUID.equals(player2) && battle.leaderUUID.equals(player1))) {
                return battle;
            }
        }
        return null;
    }

    /**
     * Beendet ein Battle und vergibt ggf. Rewards.
     */
    public void finishBattle(ActiveBattle battle, GymData.BattleResult result,
                             ServerPlayer challenger, ServerPlayer leader) {
        // Battle aus Tracking entfernen
        activeBattles.values().removeIf(ab -> 
            ab.challengerUUID.equals(battle.challengerUUID) && 
            ab.leaderUUID.equals(battle.leaderUUID));

        // Battle-Record erstellen
        GymData.BattleRecord record = new GymData.BattleRecord(
                battle.challengerUUID.toString(), battle.challengerName,
                battle.leaderUUID.toString(), battle.leaderName,
                battle.gymType.getId(), result
        );

        // Bei Challenger-Sieg: Rewards prüfen und vergeben
        if (result == GymData.BattleResult.CHALLENGER_WIN && challenger != null) {
            // Prüfen ob bereits diesen Monat geclaimt
            if (!GymData.get().hasClaimedThisMonth(battle.challengerUUID.toString(), battle.gymType.getId())) {
                grantChallengerRewards(challenger, battle.gymType);
                record.rewardsClaimed = true;
                GymData.get().markRewardClaimed(battle.challengerUUID.toString(), battle.gymType.getId());
                GymData.get().addBadge(battle.challengerUUID.toString(), battle.gymType.getId());
            } else {
                challenger.sendSystemMessage(Component.literal("ℹ ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("You already received this month's rewards for this gym.")
                                .withStyle(ChatFormatting.GRAY)));
            }
        }

        // Record speichern
        GymData.get().recordBattle(record);
        GymLogManager.logBattle(record);

        // Ergebnis-Nachrichten
        sendBattleResultMessages(challenger, leader, battle.gymType, result);

        EvolutionBoost.LOGGER.info("[gym] Battle finished: {} vs {} ({}) - Result: {}", 
                battle.challengerName, battle.leaderName, 
                battle.gymType.getDisplayName(), result);
    }

    private void grantChallengerRewards(ServerPlayer player, GymType gymType) {
        GymConfig.GymEntry gym = GymConfig.get().getGym(gymType);
        if (gym == null) return;

        GymConfig.GymRewards rewards = gym.rewards;
        
        // Badge geben
        if (rewards.badge != null && !rewards.badge.isEmpty()) {
            giveItem(player, rewards.badge, 1);
        }

        // TM geben
        if (rewards.tm != null && !rewards.tm.isEmpty()) {
            giveItem(player, rewards.tm, 1);
        }

        // Silver Coins geben
        if (rewards.silverCoins > 0) {
            giveItem(player, "evolutionboost:evolution_coin_silver", rewards.silverCoins);
        }

        // Extra Items
        if (rewards.extraItems != null) {
            for (Map.Entry<String, Integer> entry : rewards.extraItems.entrySet()) {
                giveItem(player, entry.getKey(), entry.getValue());
            }
        }

        // Log
        GymLogManager.logRewardsGiven(
                player.getGameProfile().getName(),
                player.getStringUUID(),
                gymType,
                rewards.badge,
                rewards.tm,
                rewards.silverCoins
        );

        player.sendSystemMessage(Component.literal("🎁 ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("You received the ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(gymType.getDisplayName() + " Badge")
                        .withStyle(gymType.getColor(), ChatFormatting.BOLD))
                .append(Component.literal(" and rewards!")
                        .withStyle(ChatFormatting.WHITE)));
    }

    private void giveItem(ServerPlayer player, String itemId, int count) {
        try {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc == null) return;
            
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item == null) return;
            
            ItemStack stack = new ItemStack(item, count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[gym] Failed to give item {}: {}", itemId, e.getMessage());
        }
    }

    private void sendBattleResultMessages(ServerPlayer challenger, ServerPlayer leader, 
                                          GymType gymType, GymData.BattleResult result) {
        Component message;
        switch (result) {
            case CHALLENGER_WIN -> {
                message = Component.literal("🏆 ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Challenger wins! ")
                                .withStyle(ChatFormatting.GREEN));
            }
            case LEADER_WIN -> {
                message = Component.literal("🛡 ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("The Gym Leader defends their title!")
                                .withStyle(ChatFormatting.AQUA));
            }
            case DRAW -> {
                message = Component.literal("⚖ ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("The battle ended in a draw.")
                                .withStyle(ChatFormatting.YELLOW));
            }
            case CANCELLED -> {
                message = Component.literal("✗ ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal("The battle was cancelled.")
                                .withStyle(ChatFormatting.GRAY));
            }
            default -> {
                return;
            }
        }

        if (challenger != null) challenger.sendSystemMessage(message);
        if (leader != null) leader.sendSystemMessage(message);
    }

    // ==================== Pending Challenge Helpers ====================

    public void addPendingChallenge(UUID challengerUUID, PendingChallenge challenge) {
        pendingChallenges.put(challengerUUID, challenge);
    }

    public PendingChallenge getPendingChallenge(UUID challengerUUID) {
        return pendingChallenges.get(challengerUUID);
    }

    public PendingChallenge removePendingChallenge(UUID challengerUUID) {
        return pendingChallenges.remove(challengerUUID);
    }

    public boolean leaderHasPendingChallenge(UUID leaderUUID) {
        for (PendingChallenge challenge : pendingChallenges.values()) {
            if (challenge.leaderUUID.equals(leaderUUID)) {
                return true;
            }
        }
        return false;
    }

    public PendingChallenge getPendingChallengeForLeader(UUID leaderUUID) {
        for (Map.Entry<UUID, PendingChallenge> entry : pendingChallenges.entrySet()) {
            if (entry.getValue().leaderUUID.equals(leaderUUID)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public void cleanupExpiredChallenges() {
        int timeout = GymConfig.get().challengeTimeoutSeconds * 1000;
        long now = System.currentTimeMillis();
        
        pendingChallenges.entrySet().removeIf(entry -> 
                now - entry.getValue().timestamp > timeout);
    }

    // ==================== Reward Eligibility für RewardManager ====================

    /**
     * Prüft ob ein Spieler berechtigt ist für Gym Monthly Rewards.
     * Wird vom RewardManager aufgerufen.
     */
    public boolean isEligibleForGymMonthlyReward(ServerPlayer player) {
        // Muss ein Leader sein
        if (!isAnyLeader(player)) {
            return false;
        }
        // Muss genug Kämpfe haben
        return GymData.get().leaderHasEnoughBattles(player.getStringUUID());
    }

    /**
     * Holt die Anzahl fehlender Kämpfe für Monthly Reward.
     */
    public int getMissingBattlesForReward(ServerPlayer player) {
        int current = GymData.get().getLeaderBattlesThisMonth(player.getStringUUID());
        int required = GymConfig.get().leaderMinBattlesForMonthlyReward;
        return Math.max(0, required - current);
    }

    // ==================== Data Classes ====================

    public static class PendingChallenge {
        public final UUID challengerUUID;
        public final String challengerName;
        public final UUID leaderUUID;
        public final String leaderName;
        public final GymType gymType;
        public final long timestamp;

        public PendingChallenge(UUID challengerUUID, String challengerName,
                               UUID leaderUUID, String leaderName, GymType gymType) {
            this.challengerUUID = challengerUUID;
            this.challengerName = challengerName;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.gymType = gymType;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            int timeout = GymConfig.get().challengeTimeoutSeconds * 1000;
            return System.currentTimeMillis() - timestamp > timeout;
        }
    }

    public static class ActiveBattle {
        public final UUID battleId;
        public final UUID challengerUUID;
        public final String challengerName;
        public final UUID leaderUUID;
        public final String leaderName;
        public final GymType gymType;
        public final long timestamp;

        public ActiveBattle(UUID battleId, UUID challengerUUID, String challengerName,
                           UUID leaderUUID, String leaderName, GymType gymType) {
            this.battleId = battleId;
            this.challengerUUID = challengerUUID;
            this.challengerName = challengerName;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.gymType = gymType;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
