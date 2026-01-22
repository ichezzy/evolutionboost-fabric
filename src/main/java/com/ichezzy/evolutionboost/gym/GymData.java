package com.ichezzy.evolutionboost.gym;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;

/**
 * World-basierte Daten für das Gym-System.
 * 
 * Pfad: <world>/evolutionboost/gym_data.json
 * 
 * Enthält:
 * - Battle-Historie
 * - Monthly Reward Claims
 * - Player Stats
 * - Leader Battle-Counter
 */
public final class GymData {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private static GymData INSTANCE;
    private static MinecraftServer SERVER;

    // ==================== Daten-Strukturen ====================

    /** Alle Battles (für Statistiken, gekürzt nach X Monaten) */
    public List<BattleRecord> battles = new ArrayList<>();

    /** 
     * Monthly Reward Claims: "YYYY-MM" -> "gymType" -> [player UUIDs die geclaimt haben]
     * Beispiel: "2026-01" -> "bug" -> ["uuid1", "uuid2"]
     */
    public Map<String, Map<String, Set<String>>> monthlyRewardsClaimed = new HashMap<>();

    /**
     * Leader Battle Counter pro Monat: "YYYY-MM" -> "leaderUUID" -> battleCount
     * Für die Mindestanzahl Kämpfe für Monthly Rewards
     */
    public Map<String, Map<String, Integer>> leaderBattlesPerMonth = new HashMap<>();

    /**
     * Player Stats (persistent)
     */
    public Map<String, PlayerGymStats> playerStats = new HashMap<>();

    // ==================== Record-Klassen ====================

    public static class BattleRecord {
        public String id;
        public String challengerUUID;
        public String challengerName;
        public String leaderUUID;
        public String leaderName;
        public String gymType;
        public String timestamp;
        public BattleResult result;
        public boolean rewardsClaimed;

        public BattleRecord() {}

        public BattleRecord(String challengerUUID, String challengerName,
                           String leaderUUID, String leaderName,
                           String gymType, BattleResult result) {
            this.id = UUID.randomUUID().toString();
            this.challengerUUID = challengerUUID;
            this.challengerName = challengerName;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.gymType = gymType;
            this.timestamp = Instant.now().toString();
            this.result = result;
            this.rewardsClaimed = false;
        }
    }

    public enum BattleResult {
        CHALLENGER_WIN,
        LEADER_WIN,
        DRAW,
        CANCELLED
    }

    public static class PlayerGymStats {
        public int totalBattles = 0;
        public int wins = 0;
        public int losses = 0;
        public Set<String> badgesEarned = new HashSet<>(); // Gym-Types
        public int battlesAsLeader = 0;
        public int winsAsLeader = 0;

        public double getWinRate() {
            if (totalBattles == 0) return 0;
            return (double) wins / totalBattles * 100;
        }
    }

    // ==================== Singleton & IO ====================

    private GymData() {}

    public static void init(MinecraftServer server) {
        SERVER = server;
        INSTANCE = load();
        EvolutionBoost.LOGGER.info("[gym] GymData initialized");
    }

    public static GymData get() {
        if (INSTANCE == null) {
            INSTANCE = new GymData();
        }
        return INSTANCE;
    }

    private static Path getDataFile() {
        if (SERVER == null) {
            return Path.of("config", "evolutionboost", "gym_data.json");
        }
        Path worldDir = SERVER.getWorldPath(LevelResource.ROOT).resolve("evolutionboost");
        try { Files.createDirectories(worldDir); } catch (Exception ignored) {}
        return worldDir.resolve("gym_data.json");
    }

    private static GymData load() {
        Path file = getDataFile();
        if (Files.exists(file)) {
            try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                GymData data = GSON.fromJson(br, GymData.class);
                if (data != null) {
                    data.ensureDefaults();
                    return data;
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.warn("[gym] Failed to load GymData: {}", e.getMessage());
            }
        }
        return new GymData();
    }

    public static void save() {
        if (INSTANCE == null) return;
        try {
            Path file = getDataFile();
            Files.createDirectories(file.getParent());
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[gym] Failed to save GymData: {}", e.getMessage());
        }
    }

    private void ensureDefaults() {
        if (battles == null) battles = new ArrayList<>();
        if (monthlyRewardsClaimed == null) monthlyRewardsClaimed = new HashMap<>();
        if (leaderBattlesPerMonth == null) leaderBattlesPerMonth = new HashMap<>();
        if (playerStats == null) playerStats = new HashMap<>();
    }

    // ==================== Battle Recording ====================

    /**
     * Speichert einen Battle-Record.
     */
    public void recordBattle(BattleRecord record) {
        battles.add(record);
        
        // Leader Battle Counter erhöhen
        String month = getCurrentMonth();
        leaderBattlesPerMonth.computeIfAbsent(month, k -> new HashMap<>());
        Map<String, Integer> monthMap = leaderBattlesPerMonth.get(month);
        monthMap.merge(record.leaderUUID, 1, Integer::sum);

        // Player Stats updaten
        updateStatsAfterBattle(record);

        save();
    }

    private void updateStatsAfterBattle(BattleRecord record) {
        // Challenger Stats
        PlayerGymStats challengerStats = playerStats.computeIfAbsent(
                record.challengerUUID, k -> new PlayerGymStats());
        challengerStats.totalBattles++;
        if (record.result == BattleResult.CHALLENGER_WIN) {
            challengerStats.wins++;
        } else if (record.result == BattleResult.LEADER_WIN) {
            challengerStats.losses++;
        }

        // Leader Stats
        PlayerGymStats leaderStats = playerStats.computeIfAbsent(
                record.leaderUUID, k -> new PlayerGymStats());
        leaderStats.battlesAsLeader++;
        if (record.result == BattleResult.LEADER_WIN) {
            leaderStats.winsAsLeader++;
        }
    }

    // ==================== Monthly Rewards ====================

    /**
     * Prüft ob ein Spieler diesen Monat bereits Rewards für ein Gym geclaimt hat.
     */
    public boolean hasClaimedThisMonth(String playerUUID, String gymType) {
        String month = getCurrentMonth();
        Map<String, Set<String>> gymMap = monthlyRewardsClaimed.get(month);
        if (gymMap == null) return false;
        Set<String> claimed = gymMap.get(gymType);
        return claimed != null && claimed.contains(playerUUID);
    }

    /**
     * Markiert dass ein Spieler Rewards geclaimt hat.
     */
    public void markRewardClaimed(String playerUUID, String gymType) {
        String month = getCurrentMonth();
        monthlyRewardsClaimed
                .computeIfAbsent(month, k -> new HashMap<>())
                .computeIfAbsent(gymType, k -> new HashSet<>())
                .add(playerUUID);
        save();
    }

    /**
     * Fügt ein Badge zu den Player Stats hinzu.
     */
    public void addBadge(String playerUUID, String gymType) {
        PlayerGymStats stats = playerStats.computeIfAbsent(playerUUID, k -> new PlayerGymStats());
        stats.badgesEarned.add(gymType);
        save();
    }

    // ==================== Leader Stats ====================

    /**
     * Holt die Anzahl Kämpfe eines Leaders diesen Monat.
     */
    public int getLeaderBattlesThisMonth(String leaderUUID) {
        String month = getCurrentMonth();
        Map<String, Integer> monthMap = leaderBattlesPerMonth.get(month);
        if (monthMap == null) return 0;
        return monthMap.getOrDefault(leaderUUID, 0);
    }

    /**
     * Prüft ob ein Leader genug Kämpfe für Monthly Rewards hat.
     */
    public boolean leaderHasEnoughBattles(String leaderUUID) {
        int required = GymConfig.get().leaderMinBattlesForMonthlyReward;
        return getLeaderBattlesThisMonth(leaderUUID) >= required;
    }

    // ==================== Player Stats ====================

    /**
     * Holt die Stats eines Spielers.
     */
    public PlayerGymStats getPlayerStats(String playerUUID) {
        return playerStats.computeIfAbsent(playerUUID, k -> new PlayerGymStats());
    }

    /**
     * Prüft ob ein Spieler ein bestimmtes Badge hat.
     */
    public boolean hasBadge(String playerUUID, String gymType) {
        PlayerGymStats stats = playerStats.get(playerUUID);
        return stats != null && stats.badgesEarned.contains(gymType);
    }

    // ==================== Utility ====================

    private static String getCurrentMonth() {
        return YearMonth.now(ZoneId.of("Europe/Berlin")).toString(); // "2026-01"
    }

    /**
     * Cleanup: Alte Daten entfernen (älter als X Monate).
     */
    public void cleanup(int keepMonths) {
        YearMonth cutoff = YearMonth.now().minusMonths(keepMonths);
        
        // Alte Monthly Claims entfernen
        monthlyRewardsClaimed.keySet().removeIf(month -> {
            try {
                return YearMonth.parse(month).isBefore(cutoff);
            } catch (Exception e) {
                return false;
            }
        });

        // Alte Leader Battle Counts entfernen
        leaderBattlesPerMonth.keySet().removeIf(month -> {
            try {
                return YearMonth.parse(month).isBefore(cutoff);
            } catch (Exception e) {
                return false;
            }
        });

        // Alte Battles entfernen (aber Stats behalten)
        Instant cutoffInstant = cutoff.atDay(1).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant();
        battles.removeIf(b -> {
            try {
                return Instant.parse(b.timestamp).isBefore(cutoffInstant);
            } catch (Exception e) {
                return false;
            }
        });

        save();
    }
}
