package com.ichezzy.evolutionboost.configs;

import com.google.gson.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Reward-Eligibility / Definitionen
 * Pfad: /config/evolutionboost/rewards/eligibility.json
 *
 * Struktur:
 * {
 *   "rewards": {
 *     "DAILY":   [ "evolutionboost:evolution_coin_bronze", { "id": "minecraft:diamond", "count": 2 } ],
 *     "WEEKLY":  [ { "id": "evolutionboost:evolution_coin_silver", "count": 2 } ],
 *     "MONTHLY": [ { "id": "evolutionboost:evolution_coin_gold",   "count": 1 } ],
 *     "GYM_LEADER_MONTHLY": [ "evolutionboost:event_voucher_shiny", { "id": "evolutionboost:evolution_coin_gold", "count": 2 } ],
 *     "gym_leader": [ { "id": "minecraft:diamond", "count": 32 } ],
 *     "donator":    [ "minecraft:emerald" ]
 *   },
 *   "requiredPermission": {
 *     "GYM_LEADER_MONTHLY": "evolutionboost.rewards.gymleader",
 *     "gym_leader": "evolutionboost.rewards.gymleader"
 *   }
 * }
 */
public final class RewardConfig {

    /** Ein Reward ist eine Liste von Item-Einträgen pro Typ/Gruppe. */
    public Map<String, List<RewardItem>> rewards = new LinkedHashMap<>();

    /** Optional: Permissionnode pro Typ/Gruppe (z.B. für Gym-Leader). */
    public Map<String, String> requiredPermission = new HashMap<>();

    public static final class RewardItem {
        public String id;   // z.B. "minecraft:diamond"
        public int count = 1;
        public RewardItem() {}
        public RewardItem(String id, int count) { this.id = id; this.count = count; }
    }

    private static volatile RewardConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(RewardItem.class, new JsonDeserializer<RewardItem>() {
                @Override
                public RewardItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                        // "minecraft:diamond"
                        return new RewardItem(json.getAsString(), 1);
                    }
                    if (json.isJsonObject()) {
                        JsonObject o = json.getAsJsonObject();
                        String id = o.has("id") ? o.get("id").getAsString() : "minecraft:air";
                        int count = o.has("count") ? o.get("count").getAsInt() : 1;
                        return new RewardItem(id, count);
                    }
                    throw new JsonParseException("Invalid RewardItem: " + json);
                }
            })
            .setPrettyPrinting()
            .create();

    private RewardConfig() {}

    public static RewardConfig get() {
        RewardConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized RewardConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("eligibility.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, RewardConfig.class);
                }
            } else {
                INSTANCE = defaults();
                try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(INSTANCE, bw);
                }
            }
        } catch (Exception e) {
            INSTANCE = defaults();
        }
        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("eligibility.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception ignored) {}
    }

    /** Default, falls Datei noch nicht existiert. */
    public static RewardConfig defaults() {
        RewardConfig c = new RewardConfig();
        c.rewards.put("DAILY",   List.of(new RewardItem("evolutionboost:evolution_coin_bronze", 3)));
        c.rewards.put("WEEKLY",  List.of(new RewardItem("evolutionboost:evolution_coin_silver", 2)));
        c.rewards.put("MONTHLY", List.of(new RewardItem("evolutionboost:evolution_coin_gold",   1)));
        c.rewards.put("GYM_LEADER_MONTHLY", List.of(
                new RewardItem("evolutionboost:event_voucher_shiny", 1),
                new RewardItem("evolutionboost:evolution_coin_gold", 2)
        ));
        c.requiredPermission.put("GYM_LEADER_MONTHLY", "evolutionboost.rewards.gymleader");

        // Beispiele für frei definierbare Gruppen:
        c.rewards.put("gym_leader", List.of(new RewardItem("minecraft:diamond", 32)));
        c.rewards.put("donator",    List.of(new RewardItem("minecraft:emerald", 1)));
        c.requiredPermission.put("gym_leader", "evolutionboost.rewards.gymleader");

        return c;
    }
}
