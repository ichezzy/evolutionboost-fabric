package com.ichezzy.evolutionboost.quest;

/**
 * Kategorien von Quests.
 * Bestimmt Verhalten und Darstellung.
 */
public enum QuestCategory {
    /**
     * Haupt-Quests - sequentiell, Story-relevant.
     */
    MAIN("Main Quest", true),

    /**
     * Neben-Quests - optional, können parallel sein.
     */
    SIDE("Side Quest", false),

    /**
     * Tägliche Quests - resetten täglich.
     */
    DAILY("Daily Quest", false),

    /**
     * Wöchentliche Quests - resetten wöchentlich.
     */
    WEEKLY("Weekly Quest", false),

    /**
     * Monatliche Quests - resetten monatlich.
     */
    MONTHLY("Monthly Quest", false),

    /**
     * Event-Quests - zeitlich begrenzt.
     */
    EVENT("Event Quest", false),

    /**
     * Achievement-artige Quests - einmalig, permanent.
     */
    ACHIEVEMENT("Achievement", false);

    private final String displayName;
    private final boolean sequential;

    QuestCategory(String displayName, boolean sequential) {
        this.displayName = displayName;
        this.sequential = sequential;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Ob Quests dieser Kategorie sequentiell sind (eine nach der anderen).
     */
    public boolean isSequential() {
        return sequential;
    }

    /**
     * Ob Quests dieser Kategorie zurückgesetzt werden können.
     */
    public boolean isRepeatable() {
        return this == DAILY || this == WEEKLY || this == MONTHLY;
    }
}
