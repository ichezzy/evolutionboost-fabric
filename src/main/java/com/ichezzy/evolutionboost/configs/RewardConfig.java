package com.ichezzy.evolutionboost.configs;

import com.google.gson.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Definition der tatsächlichen Rewards pro Typ.
 *
 * Pfad: /config/evolutionboost/rewards/rewards.json
 *
 * Struktur (Beispiel):
 * {
 *   "rewards": {
 *     "DAILY": [
 *       "evolutionboost:evolution_coin_bronze",
 *       { "id": "minecraft:diamond", "count": 3 }
 *     ],
 *     "WEEKLY": [
 *       { "id": "evolutionboost:evolution_coin_silver", "count": 2 }
 *     ],
 *     "MONTHLY_DONATOR_COPPER": [
 *       { "id": "evolutionboost:evolution_coin_silver", "count": 10 },
 *       { "id": "wanteditems:gold_candy_lucky_box", "count": 10 },
 *       { "id": "wanteditems:cobblemon_lucky_box", "count": 10 },
 *       { "id": "wanteditems:ancient_poke_ball_lucky_box", "count": 10 }
 *     ],
 *     "MONTHLY_DONATOR_SILVER": [
 *       { "id": "evolutionboost:evolution_coin_silver", "count": 15 },
 *       { "id": "wanteditems:gold_candy_lucky_box", "count": 10 },
 *       { "id": "wanteditems:cobblemon_lucky_box", "count": 10 },
 *       { "id": "wanteditems:ancient_poke_ball_lucky_box", "count": 10 },
 *       { "id": "evolutionboost:event_voucher_blank", "count": 1 }
 *     ],
 *     "MONTHLY_DONATOR_GOLD": [
 *       { "id": "evolutionboost:evolution_coin_gold", "count": 1 },
 *       { "id": "wanteditems:gold_candy_lucky_box", "count": 10 },
 *       { "id": "wanteditems:cobblemon_lucky_box", "count": 10 },
 *       { "id": "wanteditems:ancient_poke_ball_lucky_box", "count": 10 },
 *       { "id": "evolutionboost:event_voucher_blank", "count": 1 }
 *     ],
 *     "MONTHLY_GYM": [
 *       { "id": "evolutionboost:evolution_coin_gold", "count": 1 }
 *     ],
 *     "MONTHLY_STAFF": [
 *       { "id": "evolutionboost:evolution_coin_gold", "count": 1 }
 *     ]
 *   },
 *   "requiredPermission": {
 *   }
 * }
 */
public final class RewardConfig {

    /** Ein Reward ist eine Liste von Item-Einträgen pro Typ/Gruppe. */
    public Map<String, List<RewardItem>> rewards = new LinkedHashMap<>();

    /** Optional: Permissionnode pro Typ/Gruppe (z.B. für Spezialrollen). */
    public Map<String, String> requiredPermission = new HashMap<>();

    public static final class RewardItem {
        public String id;   // z.B. "minecraft:diamond"
        public int count = 1;

        public RewardItem() {}
        public RewardItem(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }

    private static volatile RewardConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(RewardItem.class, new JsonDeserializer<RewardItem>() {
                @Override
                public RewardItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                        // Einfacher String: "minecraft:diamond"
                        return new RewardItem(json.getAsString(), 1);
                    }
                    if (json.isJsonObject()) {
                        JsonObject o = json.getAsJsonObject();
                        String id = o.has("id") ? o.get("id").getAsString() : "minecraft:air";
                        int count = 1;
                        if (o.has("count")) {
                            JsonElement cEl = o.get("count");
                            // erlaubt sowohl Zahl als auch String "3"
                            if (cEl.isJsonPrimitive()) {
                                JsonPrimitive prim = cEl.getAsJsonPrimitive();
                                if (prim.isNumber()) {
                                    count = prim.getAsInt();
                                } else if (prim.isString()) {
                                    try {
                                        count = Integer.parseInt(prim.getAsString());
                                    } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                        if (count <= 0) count = 1;
                        return new RewardItem(id, count);
                    }
                    throw new JsonParseException("Invalid RewardItem: " + json);
                }
            })
            .setPrettyPrinting()
            .create();

    private RewardConfig() {}

    /**
     * WICHTIG: Wir lesen jetzt **bei jedem Aufruf** von der Datei.
     * So werden Änderungen in rewards.json ohne Server-Restart oder /reload wirksam.
     */
    public static RewardConfig get() {
        return loadOrCreate();
    }

    public static synchronized RewardConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("rewards.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, RewardConfig.class);
                    if (INSTANCE == null) {
                        INSTANCE = defaults();
                    }
                }
            } else {
                INSTANCE = defaults();
                try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(INSTANCE, bw);
                }
            }
        } catch (Exception e) {
            // Im Fehlerfall auf Defaults zurückfallen
            INSTANCE = defaults();
        }
        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("rewards.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception ignored) {}
    }

    /** Default-Config, falls Datei noch nicht existiert oder nicht geparst werden kann. */
    public static RewardConfig defaults() {
        RewardConfig c = new RewardConfig();

        // DAILY: 5x Bronze-Münze
        c.rewards.put("DAILY", List.of(
                new RewardItem("evolutionboost:evolution_coin_bronze", 5)
        ));

        // WEEKLY: 1x Silber-Münze
        c.rewards.put("WEEKLY", List.of(
                new RewardItem("evolutionboost:evolution_coin_silver", 1)
        ));

        // MONTHLY_DONATOR_COPPER:
        // 10x Evolution Silver Coin
        // 10x Loot Boxes of each XP, Cobblemon Items and Pokéballs
        c.rewards.put("MONTHLY_DONATOR_COPPER", List.of(
                new RewardItem("evolutionboost:evolution_coin_silver", 10),
                new RewardItem("wanteditems:gold_candy_lucky_box", 10),        // XP
                new RewardItem("wanteditems:cobblemon_lucky_box", 10),         // Cobblemon Items
                new RewardItem("wanteditems:ancient_poke_ball_lucky_box", 10)  // Pokéballs
        ));

        // MONTHLY_DONATOR_SILVER:
        // 15x Evolution Silver Coin
        // 10x Loot Boxes of each XP, Cobblemon Items and Pokéballs
        // 1x Event Voucher (blank)
        c.rewards.put("MONTHLY_DONATOR_SILVER", List.of(
                new RewardItem("evolutionboost:evolution_coin_silver", 15),
                new RewardItem("wanteditems:gold_candy_lucky_box", 10),
                new RewardItem("wanteditems:cobblemon_lucky_box", 10),
                new RewardItem("wanteditems:ancient_poke_ball_lucky_box", 10),
                new RewardItem("evolutionboost:event_voucher_blank", 1)
        ));

        // MONTHLY_DONATOR_GOLD:
        // 1x Evolution Gold Coin
        // 10x Loot Boxes of each XP, Cobblemon Items and Pokéballs
        // 1x Event Voucher (blank)
        c.rewards.put("MONTHLY_DONATOR_GOLD", List.of(
                new RewardItem("evolutionboost:evolution_coin_gold", 1),
                new RewardItem("wanteditems:gold_candy_lucky_box", 10),
                new RewardItem("wanteditems:cobblemon_lucky_box", 10),
                new RewardItem("wanteditems:ancient_poke_ball_lucky_box", 10),
                new RewardItem("evolutionboost:event_voucher_blank", 1)
        ));

        // Fallback für alte Configs (wird von RewardManager nur genutzt, wenn jemand explizit "MONTHLY_DONATOR" referenziert)
        c.rewards.put("MONTHLY_DONATOR", List.of(
                new RewardItem("evolutionboost:evolution_coin_gold", 1)
        ));

        // MONTHLY_GYM
        c.rewards.put("MONTHLY_GYM", List.of(
                new RewardItem("evolutionboost:evolution_coin_gold", 1)
        ));

        // MONTHLY_STAFF
        c.rewards.put("MONTHLY_STAFF", List.of(
                new RewardItem("evolutionboost:evolution_coin_gold", 1)
        ));

        return c;
    }
}
