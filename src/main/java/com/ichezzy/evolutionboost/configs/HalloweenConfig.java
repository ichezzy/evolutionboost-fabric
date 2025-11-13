package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Einfache Config für Halloween-Event.
 * Datei: config/evolutionboost/halloween.json
 * {
 *   "debug": false
 * }
 */
public final class HalloweenConfig {
    public boolean debug = false; // default: false

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile HalloweenConfig INSTANCE;

    private HalloweenConfig() {}

    public static HalloweenConfig get() {
        return INSTANCE != null ? INSTANCE : loadOrCreate();
    }

    public static synchronized HalloweenConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("halloween.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, HalloweenConfig.class);
                }
            } else {
                INSTANCE = new HalloweenConfig(); // default debug=false
                try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(INSTANCE, bw);
                }
            }
        } catch (Exception e) {
            // Fallback auf Defaults, wenn irgendwas schiefgeht
            INSTANCE = new HalloweenConfig();
        }
        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("halloween.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception ignored) {}
    }
}
