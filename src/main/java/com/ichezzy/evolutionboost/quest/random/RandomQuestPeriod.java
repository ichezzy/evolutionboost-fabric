package com.ichezzy.evolutionboost.quest.random;

import net.minecraft.ChatFormatting;

/**
 * Die verschiedenen Perioden für Random Quests.
 */
public enum RandomQuestPeriod {
    DAILY("Daily", "daily", ChatFormatting.GREEN, 5),   // Base 5 + Streak-Bonus (0-5) = 5-10 Coins
    WEEKLY("Weekly", "weekly", ChatFormatting.AQUA, 0), // Keine Streaks, 1 Silver
    MONTHLY("Monthly", "monthly", ChatFormatting.GOLD, 0); // Keine Streaks, 1 Gold

    private final String displayName;
    private final String id;
    private final ChatFormatting color;
    private final int maxStreak;

    RandomQuestPeriod(String displayName, String id, ChatFormatting color, int maxStreak) {
        this.displayName = displayName;
        this.id = id;
        this.color = color;
        this.maxStreak = maxStreak;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getId() {
        return id;
    }

    public ChatFormatting getColor() {
        return color;
    }

    /**
     * @return Maximaler Streak-Bonus (0 = kein Streak)
     */
    public int getMaxStreak() {
        return maxStreak;
    }

    /**
     * @return true wenn diese Periode Streaks unterstützt
     */
    public boolean hasStreak() {
        return maxStreak > 0;
    }

    /**
     * @return Anzahl der Objectives für diesen Quest-Typ
     */
    public int getObjectiveCount() {
        return switch (this) {
            case DAILY -> 2;
            case WEEKLY -> 3;
            case MONTHLY -> 4;
        };
    }

    /**
     * @return Das Coin-Item für die Belohnung
     */
    public String getCoinItem() {
        return switch (this) {
            case DAILY -> "evolutionboost:evolution_coin_bronze";
            case WEEKLY -> "evolutionboost:evolution_coin_silver";
            case MONTHLY -> "evolutionboost:evolution_coin_gold";
        };
    }

    /**
     * @return Basis-Coin-Anzahl (ohne Streak-Bonus)
     */
    public int getBaseCoinAmount() {
        return switch (this) {
            case DAILY -> 5;   // 5 Bronze base
            case WEEKLY -> 3;  // 3 Silver (härter als vorher)
            case MONTHLY -> 1; // 1 Gold
        };
    }

    public static RandomQuestPeriod fromId(String id) {
        for (RandomQuestPeriod period : values()) {
            if (period.id.equalsIgnoreCase(id)) {
                return period;
            }
        }
        return null;
    }
}
