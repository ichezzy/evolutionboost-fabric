package com.ichezzy.evolutionboost.gym;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapper-Klasse für Leader-History-Funktionen.
 * Delegiert an GymLogManager.
 */
public final class GymLeaderHistory {

    private static final Path LEADER_HISTORY_FILE = Path.of("config", "evolutionboost", "logs", "gym", "leader_history.log");

    private GymLeaderHistory() {}

    /**
     * Loggt wenn ein Leader eingesetzt wird.
     */
    public static void logLeaderChange(String gymTypeId, String leaderName, String leaderUUID, String setBy) {
        GymType type = GymType.fromId(gymTypeId);
        if (type != null) {
            GymLogManager.logLeaderSet(type, leaderName, leaderUUID, setBy);
        }
    }

    /**
     * Loggt wenn ein Leader entfernt wird.
     */
    public static void logLeaderRemoval(String gymTypeId, String leaderName, String leaderUUID, String reason) {
        GymType type = GymType.fromId(gymTypeId);
        if (type != null) {
            GymLogManager.logLeaderRemoved(type, leaderName, leaderUUID, "Admin", reason);
        }
    }

    /**
     * Holt die letzten N Zeilen der Leader-Historie.
     */
    public static List<String> getHistory(int lines) {
        if (!Files.exists(LEADER_HISTORY_FILE)) {
            return Collections.emptyList();
        }
        
        try {
            List<String> allLines = Files.readAllLines(LEADER_HISTORY_FILE, StandardCharsets.UTF_8);
            int start = Math.max(0, allLines.size() - lines);
            return new ArrayList<>(allLines.subList(start, allLines.size()));
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
