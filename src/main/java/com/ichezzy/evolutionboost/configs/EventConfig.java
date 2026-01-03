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
 * Event-Konfiguration für saisonale Events.
 * Pfad: /config/evolutionboost/event.json
 *
 * Jedes Event kann komplett deaktiviert werden, um Performance zu sparen.
 */
public final class EventConfig {

    // ==================== Christmas Event ====================

    public ChristmasSettings christmas = new ChristmasSettings();

    public static final class ChristmasSettings {
        /** 
         * Master-Schalter für das Christmas Weather System.
         * Wenn false: Keine Tick-Verarbeitung, keine Boosts, keine Stürme.
         * Spart Performance wenn das Event nicht aktiv ist.
         */
        public boolean weatherEnabled = false;

        /** Ob der automatische Sturm-Zyklus aktiviert ist */
        public boolean autoStormEnabled = false;

        /** Basis-Multiplikator für Christmas (ohne Sturm) - gilt für SHINY, XP, IV */
        public double baseMultiplier = 1.5;

        /** Multiplikator während des Sturms - gilt für SHINY, XP, IV */
        public double stormMultiplier = 2.0;

        /** Sturm-Intervall in Minuten (wenn Auto aktiviert) */
        public int stormEveryMinutes = 60;

        /** Sturm-Dauer in Minuten */
        public int stormDurationMinutes = 6;
    }

    // ==================== Halloween Event (Placeholder) ====================

    public HalloweenSettings halloween = new HalloweenSettings();

    public static final class HalloweenSettings {
        /** Master-Schalter für das Halloween Event */
        public boolean enabled = false;

        /** Basis-Multiplikator für Halloween */
        public double baseMultiplier = 1.5;
    }

    // ==================== Easter Event (Placeholder) ====================

    public EasterSettings easter = new EasterSettings();

    public static final class EasterSettings {
        /** Master-Schalter für das Easter Event */
        public boolean enabled = false;

        /** Basis-Multiplikator für Easter */
        public double baseMultiplier = 1.5;
    }

    // ==================== Lunar Revel Event (Placeholder) ====================

    public LunarRevelSettings lunarRevel = new LunarRevelSettings();

    public static final class LunarRevelSettings {
        /** Master-Schalter für das Lunar Revel Event */
        public boolean enabled = false;

        /** Basis-Multiplikator für Lunar Revel */
        public double baseMultiplier = 1.5;
    }

    // ==================== Singleton & IO ====================

    private static volatile EventConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private EventConfig() {}

    public static EventConfig get() {
        EventConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized EventConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("event.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, EventConfig.class);
                    if (INSTANCE == null) {
                        INSTANCE = defaults();
                    }
                }
            } else {
                INSTANCE = defaults();
                save();
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[event] Failed to load event.json: {}", e.getMessage());
            INSTANCE = defaults();
        }

        // Ensure nested objects exist
        ensureDefaults();

        return INSTANCE;
    }

    private static void ensureDefaults() {
        if (INSTANCE.christmas == null) {
            INSTANCE.christmas = new ChristmasSettings();
        }
        if (INSTANCE.halloween == null) {
            INSTANCE.halloween = new HalloweenSettings();
        }
        if (INSTANCE.easter == null) {
            INSTANCE.easter = new EasterSettings();
        }
        if (INSTANCE.lunarRevel == null) {
            INSTANCE.lunarRevel = new LunarRevelSettings();
        }

        // Validate values
        if (INSTANCE.christmas.baseMultiplier <= 0) {
            INSTANCE.christmas.baseMultiplier = 1.5;
        }
        if (INSTANCE.christmas.stormMultiplier <= 0) {
            INSTANCE.christmas.stormMultiplier = 2.0;
        }
        if (INSTANCE.christmas.stormEveryMinutes <= 0) {
            INSTANCE.christmas.stormEveryMinutes = 60;
        }
        if (INSTANCE.christmas.stormDurationMinutes <= 0) {
            INSTANCE.christmas.stormDurationMinutes = 6;
        }
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("event.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
            EvolutionBoost.LOGGER.debug("[event] Saved event.json");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[event] Failed to save event.json: {}", e.getMessage());
        }
    }

    public static synchronized void reload() {
        INSTANCE = null;
        loadOrCreate();
        EvolutionBoost.LOGGER.info("[event] Event config reloaded.");
    }

    private static EventConfig defaults() {
        EventConfig c = new EventConfig();
        c.christmas = new ChristmasSettings();
        c.halloween = new HalloweenSettings();
        c.easter = new EasterSettings();
        c.lunarRevel = new LunarRevelSettings();
        return c;
    }

    // ==================== Convenience Methods ====================

    /**
     * Prüft ob das Christmas Weather System aktiviert ist.
     */
    public static boolean isChristmasWeatherEnabled() {
        return get().christmas.weatherEnabled;
    }

    /**
     * Prüft ob der automatische Christmas Storm aktiviert ist.
     */
    public static boolean isChristmasAutoStormEnabled() {
        return get().christmas.autoStormEnabled;
    }

    /**
     * Setzt den automatischen Storm-Status und speichert.
     */
    public static void setChristmasAutoStorm(boolean enabled) {
        get().christmas.autoStormEnabled = enabled;
        save();
    }

    /**
     * Setzt den Weather-Enabled-Status und speichert.
     */
    public static void setChristmasWeatherEnabled(boolean enabled) {
        get().christmas.weatherEnabled = enabled;
        save();
    }
}
