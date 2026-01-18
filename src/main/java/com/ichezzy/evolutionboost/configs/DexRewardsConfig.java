package com.ichezzy.evolutionboost.configs;

import com.google.gson.*;
import com.ichezzy.evolutionboost.EvolutionBoost;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Konfiguration für Pokédex-Rewards.
 * Pfad: /config/evolutionboost/dex_rewards.json
 *
 * Definiert Milestones basierend auf Prozent des Pokédex und deren Rewards.
 */
public final class DexRewardsConfig {

    /**
     * Master-Schalter für das gesamte Dex-Reward-System.
     * Wenn false: Alle Funktionen deaktiviert (keine Claims, keine Notifications, keine Commands).
     */
    public boolean enabled = true;

    /**
     * Gesamtanzahl der Pokémon im Spiel/Modpack.
     * Standard: 1025 (alle Pokémon bis Gen 9)
     */
    public int totalPokemonCount = 1025;

    /**
     * Liste der Milestones, sortiert nach Prozent.
     */
    public List<Milestone> milestones = new ArrayList<>();

    /**
     * Ein Milestone mit ID, Name, Prozent und Rewards.
     */
    public static final class Milestone {
        /** Eindeutige ID (lowercase, keine Leerzeichen) */
        public String id;
        
        /** Anzeigename */
        public String name;
        
        /** Benötigter Prozentsatz des Pokédex (1-100) */
        public double percent;
        
        /** Beschreibung (optional) */
        public String description = "";
        
        /** Icon für GUI (Item-ID) */
        public String icon = "minecraft:book";
        
        /** 
         * Optional: Spezifischer Dex (z.B. "cobblemon:kanto", "cobblemon:national").
         * Wenn null, wird der globale Pokédex-Fortschritt verwendet.
         */
        public String dexId = null;
        
        /** Rewards als Liste von RewardItems */
        public List<RewardItem> rewards = new ArrayList<>();
        
        /** Optional: Pokémon-Reward mit perfekten IVs */
        public PokemonReward pokemonReward = null;

        public Milestone() {}
        
        public Milestone(String id, String name, double percent, String description, String icon, List<RewardItem> rewards) {
            this.id = id;
            this.name = name;
            this.percent = percent;
            this.description = description;
            this.icon = icon;
            this.rewards = rewards;
        }
        
        public Milestone(String id, String name, double percent, String description, String icon, List<RewardItem> rewards, PokemonReward pokemonReward) {
            this(id, name, percent, description, icon, rewards);
            this.pokemonReward = pokemonReward;
        }
        
        public Milestone(String id, String name, double percent, String description, String icon, String dexId, List<RewardItem> rewards) {
            this(id, name, percent, description, icon, rewards);
            this.dexId = dexId;
        }
        
        /**
         * Berechnet die benötigte Anzahl gefangener Pokémon für diesen Milestone.
         */
        public int getRequiredCount(int totalPokemon) {
            return (int) Math.ceil(totalPokemon * percent / 100.0);
        }
    }

    /**
     * Ein Reward-Item (Item-ID + Anzahl).
     */
    public static final class RewardItem {
        public String id;
        public int count = 1;

        public RewardItem() {}
        
        public RewardItem(String id, int count) {
            this.id = id;
            this.count = count;
        }
    }
    
    /**
     * Ein Pokémon-Reward mit perfekten IVs.
     */
    public static final class PokemonReward {
        /** Erlaubt Legendary Pokémon */
        public boolean allowLegendary = false;
        
        /** Erlaubt Mythical Pokémon */
        public boolean allowMythical = false;
        
        /** Erlaubt Ultra Beasts */
        public boolean allowUltraBeasts = false;
        
        /** Erlaubt Shiny (Spieler kann wählen) */
        public boolean allowShinyChoice = false;
        
        /** Beschreibung für den Spieler */
        public String description = "A Pokémon with perfect IVs";
        
        public PokemonReward() {}
        
        public PokemonReward(boolean allowLegendary, boolean allowMythical, boolean allowUltraBeasts, boolean allowShinyChoice, String description) {
            this.allowLegendary = allowLegendary;
            this.allowMythical = allowMythical;
            this.allowUltraBeasts = allowUltraBeasts;
            this.allowShinyChoice = allowShinyChoice;
            this.description = description;
        }
    }

    // ==================== Singleton & IO ====================

    private static volatile DexRewardsConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(RewardItem.class, new JsonDeserializer<RewardItem>() {
                @Override
                public RewardItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
                        return new RewardItem(json.getAsString(), 1);
                    }
                    if (json.isJsonObject()) {
                        JsonObject o = json.getAsJsonObject();
                        String id = o.has("id") ? o.get("id").getAsString() : "minecraft:air";
                        int count = 1;
                        if (o.has("count")) {
                            JsonElement cEl = o.get("count");
                            if (cEl.isJsonPrimitive()) {
                                JsonPrimitive prim = cEl.getAsJsonPrimitive();
                                if (prim.isNumber()) {
                                    count = prim.getAsInt();
                                } else if (prim.isString()) {
                                    try { count = Integer.parseInt(prim.getAsString()); } catch (NumberFormatException ignored) {}
                                }
                            }
                        }
                        if (count <= 0) count = 1;
                        return new RewardItem(id, count);
                    }
                    throw new JsonParseException("Invalid RewardItem: " + json);
                }
            })
            .create();

    private DexRewardsConfig() {}

    public static DexRewardsConfig get() {
        DexRewardsConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized DexRewardsConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("dex_rewards.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, DexRewardsConfig.class);
                    if (INSTANCE == null) {
                        INSTANCE = defaults();
                    }
                }
            } else {
                INSTANCE = defaults();
                save();
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[dex] Failed to load dex_rewards.json: {}", e.getMessage());
            INSTANCE = defaults();
        }

        // Milestones nach Prozent sortieren
        if (INSTANCE.milestones != null) {
            INSTANCE.milestones.sort(Comparator.comparingDouble(m -> m.percent));
        }

        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost", "rewards");
            Files.createDirectories(dir);
            Path file = dir.resolve("dex_rewards.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[dex] Failed to save dex_rewards.json: {}", e.getMessage());
        }
    }

    public static synchronized void reload() {
        INSTANCE = null;
        loadOrCreate();
        EvolutionBoost.LOGGER.info("[dex] Dex rewards config reloaded.");
    }

    /**
     * Prüft ob das Dex-Reward-System aktiviert ist.
     */
    public static boolean isEnabled() {
        return get().enabled;
    }

    /**
     * Findet einen Milestone anhand seiner ID.
     * Gibt empty() zurück wenn System deaktiviert ist.
     */
    public Optional<Milestone> getMilestone(String id) {
        if (!enabled || milestones == null || id == null) return Optional.empty();
        return milestones.stream()
                .filter(m -> m.id != null && m.id.equalsIgnoreCase(id))
                .findFirst();
    }

    /**
     * Gibt alle Milestone-IDs zurück.
     * Gibt leere Liste zurück wenn System deaktiviert ist.
     */
    public List<String> getMilestoneIds() {
        if (!enabled || milestones == null) return List.of();
        return milestones.stream()
                .map(m -> m.id)
                .filter(Objects::nonNull)
                .toList();
    }

    // ==================== Default Config ====================

    public static DexRewardsConfig defaults() {
        DexRewardsConfig c = new DexRewardsConfig();
        c.enabled = true;
        c.totalPokemonCount = 1025;
        c.milestones = new ArrayList<>();

        // 2% - Youngster
        c.milestones.add(new Milestone(
                "youngster", "Youngster", 2.0,
                "Your first steps as a trainer",
                "cobblemon:poke_ball",
                List.of(
                        new RewardItem("cobblemon:red_apricorn_seed", 8),
                        new RewardItem("cobblemon:super_potion", 5),
                        new RewardItem("cobblemon:revive", 5)
                )
        ));

        // 4% - Bug Catcher
        c.milestones.add(new Milestone(
                "bugcatcher", "Bug Catcher", 4.0,
                "Catching bugs in the tall grass",
                "cobblemon:great_ball",
                List.of(
                        new RewardItem("cobblemon:pink_apricorn_seed", 8),
                        new RewardItem("cobblemon:blue_apricorn_seed", 8),
                        new RewardItem("cobblemon:full_heal", 5),
                        new RewardItem("cobblemon:ether", 5)
                )
        ));

        // 6% - Lass
        c.milestones.add(new Milestone(
                "lass", "Lass", 6.0,
                "A budding trainer",
                "cobblemon:ultra_ball",
                List.of(
                        new RewardItem("cobblemon:yellow_apricorn_seed", 8),
                        new RewardItem("cobblemon:black_apricorn_seed", 8),
                        new RewardItem("cobblemon:super_potion", 5),
                        new RewardItem("cobblemon:revive", 5)
                )
        ));

        // 8% - Bird Keeper
        c.milestones.add(new Milestone(
                "birdkeeper", "Bird Keeper", 8.0,
                "Master of the skies",
                "cobblemon:quick_ball",
                List.of(
                        new RewardItem("cobblemon:white_apricorn_seed", 8),
                        new RewardItem("cobblemon:green_apricorn_seed", 8),
                        new RewardItem("cobblemon:full_heal", 5),
                        new RewardItem("cobblemon:ether", 5)
                )
        ));

        // 10% - Hiker
        c.milestones.add(new Milestone(
                "hiker", "Hiker", 10.0,
                "Exploring every corner",
                "cobblemon:level_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_def", 1),
                        new RewardItem("cobblemon:exp_share", 1),
                        new RewardItem("cobblemon:pp_up", 2),
                        new RewardItem("cobblemon:revive", 5)
                )
        ));

        // 20% - Fisherman
        c.milestones.add(new Milestone(
                "fisherman", "Fisherman", 20.0,
                "Patient and persistent",
                "cobblemon:lure_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_spd", 1),
                        new RewardItem("cobblemon:rare_candy", 10),
                        new RewardItem("cobblemon:pp_up", 2),
                        new RewardItem("cobblemon:hyper_potion", 5)
                )
        ));

        // 25% - Pokéfan (+ Pokemon Reward: Normal only, no shiny choice)
        c.milestones.add(new Milestone(
                "pokefan", "Pokéfan", 25.0,
                "A true fan of Pokémon",
                "cobblemon:friend_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_hp", 1),
                        new RewardItem("cobblemon:master_ball", 1),
                        new RewardItem("cobblemon:ability_capsule", 1)
                ),
                new PokemonReward(false, false, false, false, "Choose any normal Pokémon with perfect IVs!")
        ));

        // 30% - School Kid
        c.milestones.add(new Milestone(
                "schoolkid", "School Kid", 30.0,
                "Knowledge is power",
                "cobblemon:heal_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_atk", 1),
                        new RewardItem("cobblemon:exp_share", 1),
                        new RewardItem("cobblemon:pp_max", 2),
                        new RewardItem("cobblemon:revive", 5)
                )
        ));

        // 40% - Black Belt
        c.milestones.add(new Milestone(
                "blackbelt", "Black Belt", 40.0,
                "Strength through discipline",
                "cobblemon:heavy_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_spa", 1),
                        new RewardItem("cobblemon:rare_candy", 10),
                        new RewardItem("cobblemon:pp_max", 2),
                        new RewardItem("cobblemon:hyper_potion", 5)
                )
        ));

        // 50% - Ace Trainer (+ Pokemon Reward: Normal only, WITH shiny choice)
        c.milestones.add(new Milestone(
                "acetrainer", "Ace Trainer", 50.0,
                "Halfway to mastery!",
                "cobblemon:moon_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_spe", 1),
                        new RewardItem("wanteditems:gold_bottle_cap", 1),
                        new RewardItem("cobblemon:master_ball", 1),
                        new RewardItem("cobblemon:ability_patch", 1)
                ),
                new PokemonReward(false, false, false, true, "Choose any normal Pokémon with perfect IVs - can be SHINY!")
        ));

        // 60% - Veteran
        c.milestones.add(new Milestone(
                "veteran", "Veteran", 60.0,
                "Battle-hardened trainer",
                "cobblemon:timer_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_def", 1),
                        new RewardItem("cobblemon:bold_mint", 1),
                        new RewardItem("cobblemon:impish_mint", 1),
                        new RewardItem("cobblemon:lax_mint", 1),
                        new RewardItem("cobblemon:relaxed_mint", 1),
                        new RewardItem("cobblemon:rare_candy", 10),
                        new RewardItem("cobblemon:full_restore", 5)
                )
        ));

        // 70% - Ranger
        c.milestones.add(new Milestone(
                "ranger", "Ranger", 70.0,
                "Protector of nature",
                "cobblemon:safari_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_spd", 1),
                        new RewardItem("cobblemon:calm_mint", 1),
                        new RewardItem("cobblemon:gentle_mint", 1),
                        new RewardItem("cobblemon:careful_mint", 1),
                        new RewardItem("cobblemon:sassy_mint", 1),
                        new RewardItem("cobblemon:rare_candy", 10),
                        new RewardItem("cobblemon:max_revive", 5)
                )
        ));

        // 75% - Master (+ Pokemon Reward: ALL Pokemon, no shiny choice)
        c.milestones.add(new Milestone(
                "master", "Master", 75.0,
                "A true Pokémon Master",
                "cobblemon:dusk_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_hp", 1),
                        new RewardItem("cobblemon:hardy_mint", 1),
                        new RewardItem("cobblemon:docile_mint", 1),
                        new RewardItem("cobblemon:serious_mint", 1),
                        new RewardItem("cobblemon:bashful_mint", 1),
                        new RewardItem("cobblemon:quirky_mint", 1),
                        new RewardItem("wanteditems:gold_bottle_cap", 1),
                        new RewardItem("cobblemon:master_ball", 1),
                        new RewardItem("cobblemon:ability_capsule", 1),
                        new RewardItem("wanteditems:shiny_swapper", 1)
                ),
                new PokemonReward(true, true, true, false, "Choose ANY Pokémon with perfect IVs - including Legendaries!")
        ));

        // 80% - Collector
        c.milestones.add(new Milestone(
                "collector", "Collector", 80.0,
                "Gotta catch 'em all!",
                "cobblemon:beast_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_atk", 1),
                        new RewardItem("cobblemon:lonely_mint", 1),
                        new RewardItem("cobblemon:adamant_mint", 1),
                        new RewardItem("cobblemon:naughty_mint", 1),
                        new RewardItem("cobblemon:brave_mint", 1),
                        new RewardItem("cobblemon:rare_candy", 10),
                        new RewardItem("cobblemon:full_restore", 5)
                )
        ));

        // 90% - Champion
        c.milestones.add(new Milestone(
                "champion", "Champion", 90.0,
                "The very best!",
                "cobblemon:luxury_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_spa", 1),
                        new RewardItem("cobblemon:modest_mint", 1),
                        new RewardItem("cobblemon:mild_mint", 1),
                        new RewardItem("cobblemon:rash_mint", 1),
                        new RewardItem("cobblemon:quiet_mint", 1),
                        new RewardItem("cobblemon:rare_candy", 10),
                        new RewardItem("cobblemon:max_revive", 5)
                )
        ));

        // 100% - Arceus (+ Pokemon Reward: ALL Pokemon, WITH shiny choice)
        c.milestones.add(new Milestone(
                "arceus", "Arceus", 100.0,
                "You've caught them all!",
                "cobblemon:master_ball",
                List.of(
                        new RewardItem("wanteditems:bottle_cap_spe", 1),
                        new RewardItem("cobblemon:timid_mint", 1),
                        new RewardItem("cobblemon:hasty_mint", 1),
                        new RewardItem("cobblemon:jolly_mint", 1),
                        new RewardItem("cobblemon:naive_mint", 1),
                        new RewardItem("wanteditems:gold_bottle_cap", 1),
                        new RewardItem("cobblemon:master_ball", 1),
                        new RewardItem("cobblemon:ability_patch", 1),
                        new RewardItem("wanteditems:shiny_swapper", 1)
                ),
                new PokemonReward(true, true, true, true, "Choose ANY Pokémon with perfect IVs - can be SHINY!")
        ));

        return c;
    }
}
