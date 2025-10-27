package com.ichezzy.evolutionboost.reward;

import java.util.Locale;

public enum RewardType {
    DAILY,
    WEEKLY,
    MONTHLY_DONATOR,
    MONTHLY_GYM;

    public static RewardType from(String s) {
        if (s == null) return null;
        try {
            return RewardType.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
