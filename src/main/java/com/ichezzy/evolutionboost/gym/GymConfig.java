package com.ichezzy.evolutionboost.gym;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Konfiguration für das Gym-System.
 * 
 * Pfad: config/evolutionboost/gyms.json
 * 
 * Enthält:
 * - Globale Einstellungen (Timeout, min. Kämpfe für Leader-Rewards)
 * - Pro Gym: Rewards, aktueller Leader, etc.
 */
public final class GymConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Path.of("config", "evolutionboost", "gyms.json");

    private static GymConfig INSTANCE;

    // ==================== Globale Einstellungen ====================

    /** Mindestanzahl Kämpfe für Leader um Monthly Rewards zu bekommen */
    public int leaderMinBattlesForMonthlyReward = 10;

    /** Timeout für Challenge-Anfragen in Sekunden */
    public int challengeTimeoutSeconds = 60;

    /** Maximale Distanz für Challenge (in Blöcken) */
    public int challengeRadius = 50;

    // ==================== Gym-Konfigurationen ====================

    /** Konfiguration pro Gym-Typ */
    public Map<String, GymEntry> gyms = new HashMap<>();

    // ==================== Gym-Entry Struktur ====================

    public static class GymEntry {
        /** Anzeigename (z.B. "Bug Gym") */
        public String displayName = "";

        /** Aktueller Leader (Spielername) */
        public String currentLeader = null;

        /** UUID des aktuellen Leaders */
        public String currentLeaderUUID = null;

        /** Datum wann der Leader eingesetzt wurde (ISO-8601) */
        public String leaderStartDate = null;

        /** Rewards für Herausforderer bei Sieg */
        public GymRewards rewards = new GymRewards();

        /** Ist dieses Gym aktiv? */
        public boolean enabled = true;
    }

    public static class GymRewards {
        /** Badge Item-ID (z.B. "evolutionboost:badge_bug") */
        public String badge = "";

        /** TM Item-ID (z.B. "cobblemon:tm_x_scissor") */
        public String tm = "";

        /** Anzahl Silver Coins */
        public int silverCoins = 1;

        /** Zusätzliche Items (optional) */
        public Map<String, Integer> extraItems = new HashMap<>();
    }

    // ==================== Singleton & IO ====================

    private GymConfig() {}

    public static GymConfig get() {
        if (INSTANCE == null) {
            INSTANCE = loadOrCreate();
        }
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = loadOrCreate();
        EvolutionBoost.LOGGER.info("[gym] Config reloaded");
    }

    private static GymConfig loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());

            if (Files.exists(CONFIG_FILE)) {
                try (BufferedReader br = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                    GymConfig cfg = GSON.fromJson(br, GymConfig.class);
                    if (cfg != null) {
                        cfg.ensureDefaults();
                        return cfg;
                    }
                }
            }

            // Neue Config erstellen
            GymConfig cfg = createDefault();
            save(cfg);
            return cfg;

        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[gym] Config-Fehler: {}", e.getMessage());
            return createDefault();
        }
    }

    public static void save() {
        save(INSTANCE);
    }

    public static void save(GymConfig cfg) {
        if (cfg == null) return;
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (BufferedWriter bw = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[gym] Speichern fehlgeschlagen: {}", e.getMessage());
        }
    }

    // ==================== Defaults ====================

    private static GymConfig createDefault() {
        GymConfig cfg = new GymConfig();
        cfg.leaderMinBattlesForMonthlyReward = 10;
        cfg.challengeTimeoutSeconds = 60;
        cfg.challengeRadius = 50;
        cfg.gyms = new HashMap<>();

        // Alle Gym-Typen mit Default-Werten anlegen
        for (GymType type : GymType.values()) {
            GymEntry entry = new GymEntry();
            entry.displayName = type.getDisplayName() + " Gym";
            entry.currentLeader = null;
            entry.currentLeaderUUID = null;
            entry.leaderStartDate = null;
            entry.enabled = true;

            entry.rewards = new GymRewards();
            entry.rewards.badge = "evolutionboost:badge_" + type.getId();
            entry.rewards.tm = ""; // Muss vom Admin gesetzt werden
            entry.rewards.silverCoins = 1;

            cfg.gyms.put(type.getId(), entry);
        }

        return cfg;
    }

    /**
     * Stellt sicher, dass alle Gym-Typen in der Config existieren.
     */
    private void ensureDefaults() {
        if (gyms == null) gyms = new HashMap<>();

        for (GymType type : GymType.values()) {
            if (!gyms.containsKey(type.getId())) {
                GymEntry entry = new GymEntry();
                entry.displayName = type.getDisplayName() + " Gym";
                entry.enabled = true;
                entry.rewards = new GymRewards();
                entry.rewards.badge = "evolutionboost:badge_" + type.getId();
                entry.rewards.silverCoins = 1;
                gyms.put(type.getId(), entry);
            }
        }
    }

    // ==================== Helper ====================

    /**
     * Holt die Gym-Config für einen Typ.
     */
    public GymEntry getGym(GymType type) {
        return gyms.get(type.getId());
    }

    /**
     * Holt die Gym-Config für einen Typ (by ID).
     */
    public GymEntry getGym(String typeId) {
        return gyms.get(typeId.toLowerCase());
    }
}
