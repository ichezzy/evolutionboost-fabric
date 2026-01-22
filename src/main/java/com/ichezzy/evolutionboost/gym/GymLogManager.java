package com.ichezzy.evolutionboost.gym;

import com.ichezzy.evolutionboost.EvolutionBoost;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Logging-System für das Gym-System.
 * 
 * Struktur:
 *   config/evolutionboost/logs/gym/
 *   ├── leader_history.log          <- Alle Leader-Änderungen
 *   └── battles/
 *       ├── 2026-01.log             <- Alle Battles im Januar 2026
 *       ├── 2026-02.log
 *       └── ...
 */
public final class GymLogManager {

    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final Path LOG_BASE_DIR = Path.of("config", "evolutionboost", "logs", "gym");
    private static final Path LEADER_HISTORY_FILE = LOG_BASE_DIR.resolve("leader_history.log");
    private static final Path BATTLES_DIR = LOG_BASE_DIR.resolve("battles");

    private GymLogManager() {}

    // ==================== Leader History ====================

    /**
     * Loggt wenn ein neuer Leader eingesetzt wird.
     */
    public static void logLeaderSet(GymType gymType, String leaderName, String leaderUUID, String setBy) {
        String line = String.format("[%s] SET %s Leader: %s (UUID: %s) by %s",
                now(), gymType.getDisplayName(), leaderName, leaderUUID, setBy);
        appendToLeaderHistory(line);
    }

    /**
     * Loggt wenn ein Leader entfernt wird.
     */
    public static void logLeaderRemoved(GymType gymType, String leaderName, String leaderUUID, 
                                        String removedBy, String reason) {
        String line = String.format("[%s] REMOVED %s Leader: %s (UUID: %s) by %s - Reason: %s",
                now(), gymType.getDisplayName(), leaderName, leaderUUID, removedBy, reason);
        appendToLeaderHistory(line);
    }

    /**
     * Loggt wenn ein Leader gewechselt wird (alter entfernt, neuer eingesetzt).
     */
    public static void logLeaderChanged(GymType gymType, 
                                        String oldLeaderName, String oldLeaderUUID,
                                        String newLeaderName, String newLeaderUUID,
                                        String changedBy) {
        String line = String.format("[%s] CHANGED %s Leader: %s -> %s (by %s)",
                now(), gymType.getDisplayName(), oldLeaderName, newLeaderName, changedBy);
        appendToLeaderHistory(line);
        
        // Zusätzlich detailliert loggen
        String detail = String.format("         Old: %s (UUID: %s) | New: %s (UUID: %s)",
                oldLeaderName, oldLeaderUUID, newLeaderName, newLeaderUUID);
        appendToLeaderHistory(detail);
    }

    private static void appendToLeaderHistory(String line) {
        try {
            Files.createDirectories(LOG_BASE_DIR);
            try (BufferedWriter bw = Files.newBufferedWriter(LEADER_HISTORY_FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[gym-log] Failed to write leader history: {}", e.getMessage());
        }
    }

    // ==================== Battle Logging ====================

    /**
     * Loggt einen Gym-Battle.
     */
    public static void logBattle(GymData.BattleRecord record) {
        String resultStr = switch (record.result) {
            case CHALLENGER_WIN -> "CHALLENGER WIN";
            case LEADER_WIN -> "LEADER WIN";
            case DRAW -> "DRAW";
            case CANCELLED -> "CANCELLED";
        };

        String line = String.format("[%s] %s Gym | %s (Challenger) vs %s (Leader) -> %s%s",
                now(),
                record.gymType.toUpperCase(),
                record.challengerName,
                record.leaderName,
                resultStr,
                record.rewardsClaimed ? " [Rewards Claimed]" : "");

        appendToBattleLog(line);
    }

    /**
     * Loggt wenn Rewards vergeben werden.
     */
    public static void logRewardsGiven(String playerName, String playerUUID, GymType gymType, 
                                       String badge, String tm, int coins) {
        String line = String.format("[%s] REWARDS %s Gym | Player: %s | Badge: %s, TM: %s, Coins: %d",
                now(), gymType.getDisplayName(), playerName, badge, tm, coins);
        appendToBattleLog(line);
    }

    private static void appendToBattleLog(String line) {
        try {
            Files.createDirectories(BATTLES_DIR);
            String monthFile = YearMonth.now().format(MONTH_FMT) + ".log";
            Path logFile = BATTLES_DIR.resolve(monthFile);
            
            try (BufferedWriter bw = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                bw.write(line);
                bw.newLine();
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[gym-log] Failed to write battle log: {}", e.getMessage());
        }
    }

    // ==================== Utility ====================

    private static String now() {
        return LocalDateTime.now().format(TIMESTAMP_FMT);
    }

    /**
     * Liest die Leader-Historie (letzte N Zeilen).
     */
    public static String getLeaderHistoryTail(int lines) {
        if (!Files.exists(LEADER_HISTORY_FILE)) {
            return "No leader history found.";
        }
        
        try {
            var allLines = Files.readAllLines(LEADER_HISTORY_FILE, StandardCharsets.UTF_8);
            int start = Math.max(0, allLines.size() - lines);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < allLines.size(); i++) {
                sb.append(allLines.get(i)).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "Error reading history: " + e.getMessage();
        }
    }

    /**
     * Liest die Battle-Logs für einen bestimmten Monat.
     */
    public static String getBattleLogForMonth(YearMonth month, int lines) {
        String monthFile = month.format(MONTH_FMT) + ".log";
        Path logFile = BATTLES_DIR.resolve(monthFile);
        
        if (!Files.exists(logFile)) {
            return "No battles found for " + month;
        }
        
        try {
            var allLines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int start = Math.max(0, allLines.size() - lines);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < allLines.size(); i++) {
                sb.append(allLines.get(i)).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            return "Error reading battles: " + e.getMessage();
        }
    }
}
