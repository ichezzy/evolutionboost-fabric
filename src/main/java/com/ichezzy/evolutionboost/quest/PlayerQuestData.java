package com.ichezzy.evolutionboost.quest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Speichert den Quest-Fortschritt eines Spielers.
 */
public class PlayerQuestData {
    private final UUID playerId;
    private final String playerName;

    // Quest-ID -> QuestProgress
    private final Map<String, QuestProgress> questProgress = new ConcurrentHashMap<>();

    public PlayerQuestData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    // ==================== Quest Status ====================

    /**
     * Holt den Status einer Quest.
     */
    public QuestStatus getStatus(String questId) {
        QuestProgress progress = questProgress.get(questId);
        return progress != null ? progress.status : QuestStatus.LOCKED;
    }

    /**
     * Setzt den Status einer Quest.
     */
    public void setStatus(String questId, QuestStatus status) {
        QuestProgress progress = questProgress.computeIfAbsent(questId, k -> new QuestProgress());
        progress.status = status;

        if (status == QuestStatus.ACTIVE && progress.startedAt == null) {
            progress.startedAt = Instant.now();
        }
        if (status == QuestStatus.COMPLETED && progress.completedAt == null) {
            progress.completedAt = Instant.now();
        }
    }

    /**
     * Prüft ob eine Quest abgeschlossen ist.
     */
    public boolean isCompleted(String questId) {
        return getStatus(questId) == QuestStatus.COMPLETED;
    }

    /**
     * Prüft ob eine Quest aktiv ist.
     */
    public boolean isActive(String questId) {
        return getStatus(questId) == QuestStatus.ACTIVE;
    }

    // ==================== Objective Progress ====================

    /**
     * Holt den Fortschritt für ein Objective.
     */
    public int getObjectiveProgress(String questId, String objectiveId) {
        QuestProgress progress = questProgress.get(questId);
        if (progress == null) return 0;
        return progress.objectiveProgress.getOrDefault(objectiveId, 0);
    }

    /**
     * Setzt den Fortschritt für ein Objective.
     */
    public void setObjectiveProgress(String questId, String objectiveId, int value) {
        QuestProgress progress = questProgress.computeIfAbsent(questId, k -> new QuestProgress());
        progress.objectiveProgress.put(objectiveId, value);
    }

    /**
     * Inkrementiert den Fortschritt für ein Objective.
     * @return Der neue Fortschritt
     */
    public int incrementObjectiveProgress(String questId, String objectiveId, int amount) {
        QuestProgress progress = questProgress.computeIfAbsent(questId, k -> new QuestProgress());
        int current = progress.objectiveProgress.getOrDefault(objectiveId, 0);
        int newValue = current + amount;
        progress.objectiveProgress.put(objectiveId, newValue);
        return newValue;
    }

    /**
     * Holt den gesamten Fortschritt einer Quest als Map.
     */
    public Map<String, Integer> getAllObjectiveProgress(String questId) {
        QuestProgress progress = questProgress.get(questId);
        if (progress == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(progress.objectiveProgress);
    }

    // ==================== Timestamps ====================

    public Instant getStartedAt(String questId) {
        QuestProgress progress = questProgress.get(questId);
        return progress != null ? progress.startedAt : null;
    }

    public Instant getCompletedAt(String questId) {
        QuestProgress progress = questProgress.get(questId);
        return progress != null ? progress.completedAt : null;
    }

    // ==================== Active Quests ====================

    /**
     * Holt alle aktiven Quest-IDs.
     */
    public Set<String> getActiveQuests() {
        Set<String> active = new HashSet<>();
        for (Map.Entry<String, QuestProgress> entry : questProgress.entrySet()) {
            if (entry.getValue().status == QuestStatus.ACTIVE) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    /**
     * Holt alle abgeschlossenen Quest-IDs.
     */
    public Set<String> getCompletedQuests() {
        Set<String> completed = new HashSet<>();
        for (Map.Entry<String, QuestProgress> entry : questProgress.entrySet()) {
            if (entry.getValue().status == QuestStatus.COMPLETED) {
                completed.add(entry.getKey());
            }
        }
        return completed;
    }

    /**
     * Holt alle Quest-IDs mit Fortschritt.
     */
    public Set<String> getAllQuestIds() {
        return Collections.unmodifiableSet(questProgress.keySet());
    }

    // ==================== Reset ====================

    /**
     * Setzt eine Quest zurück (löscht allen Fortschritt).
     */
    public void resetQuest(String questId) {
        questProgress.remove(questId);
    }

    /**
     * Setzt alle Quests einer Questline zurück.
     */
    public void resetQuestLine(String questLine) {
        questProgress.entrySet().removeIf(e -> e.getKey().startsWith(questLine + ":"));
    }

    // ==================== Serialization Support ====================

    /**
     * Konvertiert zu einer Map für JSON-Serialisierung.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("playerId", playerId.toString());
        map.put("playerName", playerName);

        Map<String, Object> quests = new LinkedHashMap<>();
        for (Map.Entry<String, QuestProgress> entry : questProgress.entrySet()) {
            quests.put(entry.getKey(), entry.getValue().toMap());
        }
        map.put("quests", quests);

        return map;
    }

    /**
     * Erstellt aus einer Map (von JSON).
     */
    @SuppressWarnings("unchecked")
    public static PlayerQuestData fromMap(Map<String, Object> map) {
        UUID playerId = UUID.fromString((String) map.get("playerId"));
        String playerName = (String) map.getOrDefault("playerName", "Unknown");

        PlayerQuestData data = new PlayerQuestData(playerId, playerName);

        Map<String, Object> quests = (Map<String, Object>) map.get("quests");
        if (quests != null) {
            for (Map.Entry<String, Object> entry : quests.entrySet()) {
                Map<String, Object> progressMap = (Map<String, Object>) entry.getValue();
                QuestProgress progress = QuestProgress.fromMap(progressMap);
                data.questProgress.put(entry.getKey(), progress);
            }
        }

        return data;
    }

    // ==================== Inner Class ====================

    private static class QuestProgress {
        QuestStatus status = QuestStatus.LOCKED;
        Map<String, Integer> objectiveProgress = new HashMap<>();
        Instant startedAt;
        Instant completedAt;

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", status.name());
            map.put("objectives", new LinkedHashMap<>(objectiveProgress));
            if (startedAt != null) map.put("startedAt", startedAt.toString());
            if (completedAt != null) map.put("completedAt", completedAt.toString());
            return map;
        }

        @SuppressWarnings("unchecked")
        static QuestProgress fromMap(Map<String, Object> map) {
            QuestProgress p = new QuestProgress();
            String statusStr = (String) map.get("status");
            if (statusStr != null) {
                try {
                    p.status = QuestStatus.valueOf(statusStr);
                } catch (IllegalArgumentException ignored) {}
            }

            Map<String, Object> objectives = (Map<String, Object>) map.get("objectives");
            if (objectives != null) {
                for (Map.Entry<String, Object> e : objectives.entrySet()) {
                    if (e.getValue() instanceof Number) {
                        p.objectiveProgress.put(e.getKey(), ((Number) e.getValue()).intValue());
                    }
                }
            }

            String startedStr = (String) map.get("startedAt");
            if (startedStr != null) {
                try { p.startedAt = Instant.parse(startedStr); } catch (Exception ignored) {}
            }

            String completedStr = (String) map.get("completedAt");
            if (completedStr != null) {
                try { p.completedAt = Instant.parse(completedStr); } catch (Exception ignored) {}
            }

            return p;
        }
    }
}
