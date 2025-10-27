package com.ichezzy.evolutionboost.reward;

import java.util.*;

public final class RewardConfig {
    /** Ein Reward ist eine Liste von Item-Einträgen pro Typ. */
    public Map<String, List<RewardItem>> rewards = new LinkedHashMap<>();
    /** Optional: Permissionnode pro Typ (z.B. für Gym-Leader). */
    public Map<String, String> requiredPermission = new HashMap<>();

    public static final class RewardItem {
        public String id;   // z.B. "evolutionboost:evolution_coin_silver"
        public int count = 1;
        public RewardItem() {}
        public RewardItem(String id, int count) { this.id = id; this.count = count; }
    }

    /** Default, falls Datei noch nicht existiert. */
    public static RewardConfig defaults() {
        RewardConfig c = new RewardConfig();
        c.rewards.put("DAILY", List.of(new RewardItem("evolutionboost:evolution_coin_bronze", 3)));
        c.rewards.put("WEEKLY", List.of(new RewardItem("evolutionboost:evolution_coin_silver", 2)));
        c.rewards.put("MONTHLY", List.of(new RewardItem("evolutionboost:evolution_coin_gold", 1)));
        c.rewards.put("GYM_LEADER_MONTHLY", List.of(
                new RewardItem("evolutionboost:event_voucher_shiny", 1),
                new RewardItem("evolutionboost:evolution_coin_gold", 2)
        ));
        c.requiredPermission.put("GYM_LEADER_MONTHLY", "evolutionboost.rewards.gymleader");
        return c;
    }
}
