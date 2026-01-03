package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug-Konfiguration für EvolutionBoost.
 * Pfad: /config/evolutionboost/debug.json
 *
 * Ermöglicht das Ein-/Ausschalten von Debug-Logging für verschiedene Komponenten.
 * Standardmäßig ist ALLES ausgeschaltet für optimale Performance.
 */
public final class DebugConfig {

    // ==================== Hook Debug Logging ====================

    /** XP-Hook Debug-Logging (zeigt XP-Boost Berechnungen) */
    public boolean debugXpHook = false;

    /** EV-Hook Debug-Logging (zeigt EV-Boost Berechnungen) */
    public boolean debugEvHook = false;

    /** IV-Hook Debug-Logging (zeigt IV-Boost Berechnungen) */
    public boolean debugIvHook = false;

    /** Shiny-Hook Debug-Logging (zeigt Shiny-Boost Berechnungen) */
    public boolean debugShinyHook = false;

    // ==================== Boost System Debug ====================

    /** Dimension-Boost Debug-Logging (zeigt Dimension-Multiplikator Berechnungen) */
    public boolean debugDimensionBoosts = false;

    // ==================== Quest System Debug ====================

    /** Quest-System Debug-Logging */
    public boolean debugQuests = false;

    // ==================== Pokédex System Debug ====================

    /** Pokédex-System Debug-Logging (zeigt neue Fänge) */
    public boolean debugDex = false;

    // ==================== Weather System Debug ====================

    /** Christmas Weather Debug-Logging */
    public boolean debugChristmasWeather = false;

    // ==================== General Logging ====================

    /**
     * Boost-Anwendungs-Logging (zeigt wenn Boosts angewendet werden).
     * z.B. "[compat][xp] boosted battle XP in event:christmas from 458 to 687 (mult=1.5)"
     * Standardmäßig AN, da es nützlich ist zu sehen wann Boosts wirken.
     */
    public boolean logBoostApplications = true;

    // ==================== Singleton & IO ====================

    private static volatile DebugConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private DebugConfig() {}

    public static DebugConfig get() {
        DebugConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized DebugConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("debug.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, DebugConfig.class);
                }
            } else {
                INSTANCE = new DebugConfig();
                save();
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[config] Failed to load debug.json: {}", e.getMessage());
            INSTANCE = new DebugConfig();
        }

        if (INSTANCE == null) {
            INSTANCE = new DebugConfig();
        }

        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("debug.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[config] Failed to save debug.json: {}", e.getMessage());
        }
    }

    public static synchronized void reload() {
        INSTANCE = null;
        loadOrCreate();
        EvolutionBoost.LOGGER.info("[config] Debug config reloaded.");
    }
}
