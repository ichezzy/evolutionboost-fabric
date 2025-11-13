package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Platzhalter für Pokédex-Reward-Regeln.
 * Pfad: /config/evolutionboost/rewards/pokedex_rewards.json
 *
 * Struktur (noch offen) – sobald wir konkrete Regeln definieren, füllen wir die Felder.
 */
public final class PokedexRewardsConfig {

    public static final class Rule {
        public int percent = 50;     // Beispiel: bei 50% gefangenen Pokémon
        public String rewardType = "DAILY"; // Beispiel: verweist auf RewardConfig-Gruppe/Typ
    }

    private static volatile PokedexRewardsConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private PokedexRewardsConfig() {}

    public static PokedexRewardsConfig get() {
        PokedexRewardsConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized PokedexRewardsConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("pokedex_rewards.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, PokedexRewardsConfig.class);
                }
            } else {
                INSTANCE = new PokedexRewardsConfig();
                try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(INSTANCE, bw);
                }
            }
        } catch (Exception e) {
            INSTANCE = new PokedexRewardsConfig();
        }
        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("pokedex_rewards.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception ignored) {}
    }
}
