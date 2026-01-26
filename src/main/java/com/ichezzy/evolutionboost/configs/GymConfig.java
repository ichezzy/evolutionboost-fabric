package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.gym.GymType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Konfiguration für das Gym-System.
 * 
 * Pfad: config/evolutionboost/gym/settings.json
 * 
 * Enthält:
 * - Globale Einstellungen (Timeout, Distanz, Team-Change-Interval)
 * - Blacklists für Items und Pokémon (getrennt für Leader und Challenger)
 * - Pro Gym: Rewards, Leader, Format, etc.
 */
public final class GymConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = Path.of("config", "evolutionboost", "gym");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("settings.json");

    private static GymConfig INSTANCE;

    // ==================== Globale Einstellungen ====================

    /** Mindestanzahl Kämpfe für Leader um Monthly Rewards zu bekommen */
    public int leaderMinBattlesForMonthlyReward = 10;

    /** Timeout für Challenge-Anfragen in Sekunden */
    public int challengeTimeoutSeconds = 60;

    /** Maximale Distanz für Challenge (in Blöcken) */
    public int challengeRadius = 32;

    /** Tage zwischen Team-Änderungen (0 = unbegrenzt) */
    public int teamChangeIntervalDays = 30;

    /** Ob Team-Registrierung erforderlich ist bevor Leader Challenges annehmen kann */
    public boolean requireTeamRegistration = true;

    // ==================== Blacklists ====================

    /**
     * Items die Challenger NICHT haben dürfen.
     * Format: "namespace:item" (z.B. "cobblemon:master_ball")
     */
    public List<String> challengerBannedItems = new ArrayList<>();

    /**
     * Items die Leader NICHT haben dürfen.
     */
    public List<String> leaderBannedItems = new ArrayList<>();

    /**
     * Pokémon die Challenger NICHT im Team haben dürfen.
     * Format: "species" lowercase (z.B. "mewtwo", "arceus")
     */
    public List<String> challengerBannedPokemon = new ArrayList<>();

    /**
     * Pokémon die Leader NICHT im Team haben dürfen.
     */
    public List<String> leaderBannedPokemon = new ArrayList<>();

    /**
     * Formen die gebannt sind (für beide).
     * Format: "species-form" (z.B. "rayquaza-mega", "charizard-mega-x")
     */
    public List<String> bannedForms = new ArrayList<>();

    /**
     * Abilities die gebannt sind (für beide).
     * Format: "ability" lowercase (z.B. "moody", "shadowtag")
     */
    public List<String> bannedAbilities = new ArrayList<>();

    /**
     * Maximales Level für Gym-Battles (0 = kein Limit)
     */
    public int maxLevel = 0;

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

        /** Ist der Leader registriert? (Hat Team bestätigt) */
        public boolean leaderRegistered = false;

        /** Battle-Format: "singles" oder "doubles" */
        public String battleFormat = "singles";

        /** Rewards für Herausforderer bei Sieg */
        public GymRewards rewards = new GymRewards();

        /** Ist dieses Gym aktiv? */
        public boolean enabled = true;

        /** Gym-spezifische gebannte Pokémon (zusätzlich zu global) */
        public List<String> gymBannedPokemon = new ArrayList<>();

        /** Gym-spezifisches Max-Level (überschreibt global wenn > 0) */
        public int gymMaxLevel = 0;
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
        EvolutionBoost.LOGGER.info("[gym] Config reloaded from {}", CONFIG_FILE);
    }

    private static GymConfig loadOrCreate() {
        try {
            Files.createDirectories(CONFIG_DIR);
            
            // Migration: Alte Config verschieben falls vorhanden
            migrateOldConfig();

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

    /**
     * Migriert alte Config von config/evolutionboost/gyms.json
     */
    private static void migrateOldConfig() {
        Path oldFile = Path.of("config", "evolutionboost", "gyms.json");
        if (Files.exists(oldFile) && !Files.exists(CONFIG_FILE)) {
            try {
                // Alte Config laden
                try (BufferedReader br = Files.newBufferedReader(oldFile, StandardCharsets.UTF_8)) {
                    GymConfig oldCfg = GSON.fromJson(br, GymConfig.class);
                    if (oldCfg != null) {
                        oldCfg.ensureDefaults();
                        save(oldCfg);
                        Files.delete(oldFile);
                        EvolutionBoost.LOGGER.info("[gym] Migrated old config to new location");
                    }
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.warn("[gym] Could not migrate old config: {}", e.getMessage());
            }
        }
    }

    public static void save() {
        save(INSTANCE);
    }

    public static void save(GymConfig cfg) {
        if (cfg == null) return;
        try {
            Files.createDirectories(CONFIG_DIR);
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
        cfg.challengeRadius = 32;
        cfg.teamChangeIntervalDays = 30;
        cfg.requireTeamRegistration = true;
        cfg.maxLevel = 0;

        // Default Blacklists (Beispiele - Admin kann anpassen)
        cfg.challengerBannedItems = new ArrayList<>();
        cfg.leaderBannedItems = new ArrayList<>();
        cfg.challengerBannedPokemon = new ArrayList<>();
        cfg.leaderBannedPokemon = new ArrayList<>();
        cfg.bannedForms = new ArrayList<>();
        cfg.bannedAbilities = new ArrayList<>();

        cfg.gyms = new HashMap<>();

        // Alle Gym-Typen mit Default-Werten anlegen
        for (GymType type : GymType.values()) {
            GymEntry entry = new GymEntry();
            entry.displayName = type.getDisplayName() + " Gym";
            entry.currentLeader = null;
            entry.currentLeaderUUID = null;
            entry.leaderStartDate = null;
            entry.leaderRegistered = false;
            entry.battleFormat = "singles";
            entry.enabled = true;
            entry.gymBannedPokemon = new ArrayList<>();
            entry.gymMaxLevel = 0;

            entry.rewards = new GymRewards();
            entry.rewards.badge = "evolutionboost:badge_" + type.getId();
            entry.rewards.tm = "";
            entry.rewards.silverCoins = 1;

            cfg.gyms.put(type.getId(), entry);
        }

        return cfg;
    }

    /**
     * Stellt sicher, dass alle Felder existieren.
     */
    private void ensureDefaults() {
        if (gyms == null) gyms = new HashMap<>();
        if (challengerBannedItems == null) challengerBannedItems = new ArrayList<>();
        if (leaderBannedItems == null) leaderBannedItems = new ArrayList<>();
        if (challengerBannedPokemon == null) challengerBannedPokemon = new ArrayList<>();
        if (leaderBannedPokemon == null) leaderBannedPokemon = new ArrayList<>();
        if (bannedForms == null) bannedForms = new ArrayList<>();
        if (bannedAbilities == null) bannedAbilities = new ArrayList<>();

        for (GymType type : GymType.values()) {
            if (!gyms.containsKey(type.getId())) {
                GymEntry entry = new GymEntry();
                entry.displayName = type.getDisplayName() + " Gym";
                entry.enabled = true;
                entry.battleFormat = "singles";
                entry.rewards = new GymRewards();
                entry.rewards.badge = "evolutionboost:badge_" + type.getId();
                entry.rewards.silverCoins = 1;
                entry.gymBannedPokemon = new ArrayList<>();
                gyms.put(type.getId(), entry);
            } else {
                GymEntry entry = gyms.get(type.getId());
                if (entry.battleFormat == null) entry.battleFormat = "singles";
                if (entry.gymBannedPokemon == null) entry.gymBannedPokemon = new ArrayList<>();
                if (entry.rewards == null) {
                    entry.rewards = new GymRewards();
                    entry.rewards.badge = "evolutionboost:badge_" + type.getId();
                    entry.rewards.silverCoins = 1;
                }
            }
        }
    }

    // ==================== Helper ====================

    public GymEntry getGym(GymType type) {
        return gyms.get(type.getId());
    }

    public GymEntry getGym(String typeId) {
        return gyms.get(typeId.toLowerCase());
    }

    /**
     * Prüft ob ein Item für Challenger gebannt ist.
     */
    public boolean isItemBannedForChallenger(String itemId) {
        return challengerBannedItems.stream()
                .anyMatch(s -> s.equalsIgnoreCase(itemId));
    }

    /**
     * Prüft ob ein Item für Leader gebannt ist.
     */
    public boolean isItemBannedForLeader(String itemId) {
        return leaderBannedItems.stream()
                .anyMatch(s -> s.equalsIgnoreCase(itemId));
    }

    /**
     * Prüft ob ein Pokémon für Challenger gebannt ist (global).
     */
    public boolean isPokemonBannedForChallenger(String species) {
        return challengerBannedPokemon.stream()
                .anyMatch(s -> s.equalsIgnoreCase(species));
    }

    /**
     * Prüft ob ein Pokémon für Leader gebannt ist (global).
     */
    public boolean isPokemonBannedForLeader(String species) {
        return leaderBannedPokemon.stream()
                .anyMatch(s -> s.equalsIgnoreCase(species));
    }

    /**
     * Prüft ob eine Pokémon-Form gebannt ist.
     */
    public boolean isFormBanned(String species, String form) {
        String key = species.toLowerCase() + "-" + form.toLowerCase();
        return bannedForms.stream().anyMatch(s -> s.equalsIgnoreCase(key));
    }

    /**
     * Prüft ob eine Ability gebannt ist.
     */
    public boolean isAbilityBanned(String ability) {
        return bannedAbilities.stream()
                .anyMatch(s -> s.equalsIgnoreCase(ability));
    }

    /**
     * Prüft ob ein Pokémon für ein spezifisches Gym gebannt ist.
     */
    public boolean isPokemonBannedForGym(String species, String gymTypeId) {
        GymEntry gym = getGym(gymTypeId);
        if (gym == null || gym.gymBannedPokemon == null) return false;
        return gym.gymBannedPokemon.stream()
                .anyMatch(s -> s.equalsIgnoreCase(species));
    }

    /**
     * Gibt das effektive Max-Level für ein Gym zurück.
     */
    public int getEffectiveMaxLevel(String gymTypeId) {
        GymEntry gym = getGym(gymTypeId);
        if (gym != null && gym.gymMaxLevel > 0) {
            return gym.gymMaxLevel;
        }
        return maxLevel;
    }

    /**
     * Prüft ob ein Battle-Format gültig ist.
     */
    public static boolean isValidBattleFormat(String format) {
        return "singles".equalsIgnoreCase(format) || "doubles".equalsIgnoreCase(format);
    }
}
