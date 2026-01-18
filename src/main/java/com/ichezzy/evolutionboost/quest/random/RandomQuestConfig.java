package com.ichezzy.evolutionboost.quest.random;

import com.google.gson.*;
import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Konfiguration für das Random Quest System.
 * Definiert die Objective-Pools für Daily, Weekly und Monthly Quests.
 */
public class RandomQuestConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RandomQuestConfig INSTANCE;

    // ==================== Config Fields ====================

    /** Reset-Zeit in UTC (Format: "HH:mm") */
    public String resetTimeUTC = "00:00";

    /** Daily Quest Einstellungen */
    public PeriodConfig daily = new PeriodConfig();

    /** Weekly Quest Einstellungen */
    public PeriodConfig weekly = new PeriodConfig();

    /** Monthly Quest Einstellungen */
    public PeriodConfig monthly = new PeriodConfig();

    /** Bonus-Reward Pool (zusätzlich zu Coins) */
    public List<BonusReward> bonusRewardPool = new ArrayList<>();

    // ==================== Inner Classes ====================

    public static class PeriodConfig {
        /** Anzahl der Objectives pro Quest */
        public int objectiveCount = 2;

        /** Objective-Pool für diese Periode */
        public List<ObjectiveTemplate> pool = new ArrayList<>();
    }

    public static class ObjectiveTemplate {
        /** Typ des Objectives */
        public String type;

        /** Gewichtung für Zufallsauswahl (höher = wahrscheinlicher) */
        public int weight = 10;

        /** Minimale Menge */
        public int amountMin = 5;

        /** Maximale Menge */
        public int amountMax = 15;

        /** Mögliche Pokémon-Typen (für CATCH_TYPE, DEFEAT_TYPE) */
        public List<String> types;

        /** Mögliche Naturen (für CATCH_NATURE) */
        public List<String> natures;

        public ObjectiveTemplate() {}

        public ObjectiveTemplate(String type, int weight, int amountMin, int amountMax) {
            this.type = type;
            this.weight = weight;
            this.amountMin = amountMin;
            this.amountMax = amountMax;
        }

        public ObjectiveTemplate withTypes(List<String> types) {
            this.types = types;
            return this;
        }

        public ObjectiveTemplate withNatures(List<String> natures) {
            this.natures = natures;
            return this;
        }
    }

    public static class BonusReward {
        /** Item-ID */
        public String item;

        /** Menge */
        public int amount = 1;

        /** Chance (0.0 - 1.0) */
        public double chance = 0.5;

        /** Für welche Perioden gilt dieser Bonus */
        public List<String> periods = List.of("daily", "weekly", "monthly");

        public BonusReward() {}

        public BonusReward(String item, int amount, double chance) {
            this.item = item;
            this.amount = amount;
            this.chance = chance;
        }
    }

    // ==================== Singleton ====================

    public static RandomQuestConfig get() {
        if (INSTANCE == null) {
            INSTANCE = loadOrCreate();
        }
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = loadOrCreate();
        EvolutionBoost.LOGGER.info("[random-quests] Config reloaded");
    }

    // ==================== Load / Save ====================

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("evolutionboost")
                .resolve("quests")
                .resolve("random_quests.json");
    }

    private static RandomQuestConfig loadOrCreate() {
        Path path = getConfigPath();

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                RandomQuestConfig loaded = GSON.fromJson(json, RandomQuestConfig.class);
                if (loaded != null) {
                    EvolutionBoost.LOGGER.info("[random-quests] Loaded config with {} daily, {} weekly, {} monthly objectives",
                            loaded.daily.pool.size(), loaded.weekly.pool.size(), loaded.monthly.pool.size());
                    return loaded;
                }
            } catch (Exception e) {
                EvolutionBoost.LOGGER.warn("[random-quests] Failed to load config: {}", e.getMessage());
            }
        }

        // Neue Config mit Defaults erstellen
        RandomQuestConfig config = createDefaults();
        config.save();
        return config;
    }

    public void save() {
        Path path = getConfigPath();

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
            EvolutionBoost.LOGGER.debug("[random-quests] Saved config");
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[random-quests] Failed to save config: {}", e.getMessage());
        }
    }

    // ==================== Default Config ====================

    private static RandomQuestConfig createDefaults() {
        RandomQuestConfig config = new RandomQuestConfig();

        // Pokémon-Typen für Type-basierte Objectives
        List<String> commonTypes = List.of(
                "normal", "fire", "water", "grass", "electric",
                "ice", "fighting", "poison", "ground", "flying",
                "psychic", "bug", "rock", "ghost", "dark", "steel", "fairy"
        );

        // Naturen für Nature-basierte Objectives
        List<String> natures = List.of(
                "adamant", "bold", "brave", "calm", "careful",
                "gentle", "hasty", "impish", "jolly", "lax",
                "lonely", "mild", "modest", "naive", "naughty",
                "quiet", "rash", "relaxed", "sassy", "timid"
        );

        // ==================== DAILY ====================
        config.daily.objectiveCount = 2;
        config.daily.pool = new ArrayList<>(List.of(
                new ObjectiveTemplate("catch_any", 10, 15, 25),
                new ObjectiveTemplate("catch_type", 8, 8, 15).withTypes(commonTypes),
                new ObjectiveTemplate("defeat_wild", 10, 10, 20),
                new ObjectiveTemplate("defeat_type", 7, 5, 12).withTypes(commonTypes),
                new ObjectiveTemplate("gain_xp", 8, 5000, 15000),
                new ObjectiveTemplate("level_up", 6, 3, 8),
                new ObjectiveTemplate("win_battle", 8, 5, 15)
        ));

        // ==================== WEEKLY ====================
        config.weekly.objectiveCount = 3;
        config.weekly.pool = new ArrayList<>(List.of(
                new ObjectiveTemplate("catch_any", 6, 75, 150),
                new ObjectiveTemplate("catch_type", 10, 30, 60).withTypes(commonTypes),
                new ObjectiveTemplate("catch_ha", 6, 2, 5),
                new ObjectiveTemplate("catch_nature", 5, 5, 12).withNatures(natures),
                new ObjectiveTemplate("catch_shiny", 2, 1, 1), // Immer genau 1
                new ObjectiveTemplate("defeat_wild", 6, 50, 100),
                new ObjectiveTemplate("defeat_type", 8, 20, 40).withTypes(commonTypes),
                new ObjectiveTemplate("evolve", 7, 5, 12),
                new ObjectiveTemplate("gain_xp", 6, 30000, 75000),
                new ObjectiveTemplate("level_up", 5, 15, 30)
        ));

        // ==================== MONTHLY ====================
        config.monthly.objectiveCount = 4;
        config.monthly.pool = new ArrayList<>(List.of(
                new ObjectiveTemplate("catch_type", 8, 100, 200).withTypes(commonTypes),
                new ObjectiveTemplate("catch_shiny", 6, 2, 4),
                new ObjectiveTemplate("catch_legendary", 3, 1, 2),
                new ObjectiveTemplate("catch_ha", 7, 10, 25),
                new ObjectiveTemplate("catch_nature", 6, 20, 40).withNatures(natures),
                new ObjectiveTemplate("evolve", 8, 15, 35),
                new ObjectiveTemplate("level_up", 5, 30, 60),
                new ObjectiveTemplate("gain_xp", 5, 100000, 250000),
                new ObjectiveTemplate("defeat_type", 7, 75, 150).withTypes(commonTypes)
        ));

        // ==================== BONUS REWARDS ====================
        config.bonusRewardPool = new ArrayList<>(List.of(
                new BonusReward("cobblemon:poke_ball", 10, 0.5),
                new BonusReward("cobblemon:great_ball", 5, 0.3),
                new BonusReward("cobblemon:ultra_ball", 3, 0.15),
                new BonusReward("cobblemon:rare_candy", 1, 0.2)
        ));

        return config;
    }

    // ==================== Helper Methods ====================

    /**
     * Gibt die Config für eine bestimmte Periode zurück.
     */
    public PeriodConfig getPeriodConfig(RandomQuestPeriod period) {
        return switch (period) {
            case DAILY -> daily;
            case WEEKLY -> weekly;
            case MONTHLY -> monthly;
        };
    }
}
