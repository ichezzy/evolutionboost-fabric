package com.ichezzy.evolutionboost.reward;

public enum RewardType {
    DAILY("daily", Kind.DAILY),
    WEEKLY("weekly", Kind.WEEKLY),
    MONTHLY_DONATOR("monthly_donator", Kind.MONTHLY),
    MONTHLY_GYM("monthly_gym", Kind.MONTHLY);

    public enum Kind { DAILY, WEEKLY, MONTHLY }

    private final String id;
    private final Kind kind;

    RewardType(String id, Kind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String id()     { return id; }
    public Kind   kind()   { return kind; }

    public static RewardType from(String s) {
        if (s == null) return null;
        for (var t : values()) if (t.id.equalsIgnoreCase(s) || t.name().equalsIgnoreCase(s)) return t;
        return null;
    }
}
