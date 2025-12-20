package com.ichezzy.evolutionboost.configs;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Globale EvolutionBoost-Config (evolutionboost.json)
 * Pfad: /config/evolutionboost/evolutionboost.json
 *
 * Enthält: Event-Spawns, globale Settings und Christmas-Event-Settings.
 */
public final class EvolutionBoostConfig {

    public static final class Spawn {
        public String dimension = "minecraft:overworld";
        public int x, y, z;

        public Spawn() {}
        public Spawn(String dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x; this.y = y; this.z = z;
        }
        public ResourceLocation dimensionId() { return ResourceLocation.parse(dimension); }
        public BlockPos toBlockPos() { return new BlockPos(x, y, z); }
    }

    /** z. B. "halloween" -> Spawn, "safari" -> Spawn, "christmas" -> Spawn */
    public Map<String, Spawn> eventSpawns = new LinkedHashMap<>();

    /**
     * Permanente Dimension-Boosts:
     *
     *  "dimensionBoosts": {
     *    "minecraft:overworld": { "SHINY": 1.5, "XP": 2.0 },
     *    "event:safari":       { "XP": 3.0 }
     *  }
     *
     * Wird beim Serverstart in BoostManager übernommen.
     */
    public Map<String, Map<String, Double>> dimensionBoosts = new LinkedHashMap<>();

    // ==================== General Settings ====================

    /** Maximale erlaubte Boost-Multiplikation (z. B. 10.0) */
    public double maxBoostMultiplier = 100.0;

    /**
     * Basischance für Shiny-Rolls (1 in shinyBaseOdds).
     * Sollte zur Cobblemon-Config passen (Standard: 8192).
     * Wird für die Boost-Berechnung im ShinyHook verwendet.
     */
    public int shinyBaseOdds = 8192;

    // ==================== Shiny Charm Settings ====================

    /** Ob der Shiny Charm aktiviert ist */
    public boolean shinyCharmEnabled = true;

    /** Multiplikator für Shiny-Chance wenn Charm in der Nähe (z.B. 2.0 = doppelte Chance) */
    public double shinyCharmMultiplier = 2.0;

    /** Radius in Blöcken, in dem der Charm wirkt */
    public double shinyCharmRadius = 64.0;

    // ==================== Christmas Event Settings ====================

    /** Basis-Multiplikator für Christmas (ohne Sturm) - gilt für SHINY, XP, IV */
    public double christmasBaseMultiplier = 1.5;

    /** Multiplikator während des Sturms - gilt für SHINY, XP, IV */
    public double christmasStormMultiplier = 2.0;

    /** Sturm-Intervall in Minuten (wenn Auto aktiviert) */
    public int christmasStormEveryMinutes = 60;

    /** Sturm-Dauer in Minuten */
    public int christmasStormDurationMinutes = 6;

    // ==================== Singleton & IO ====================

    private static volatile EvolutionBoostConfig INSTANCE;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Spawn.class, new JsonDeserializer<Spawn>() {
                @Override
                public Spawn deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    JsonObject o = json.getAsJsonObject();
                    String dim = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
                    int x = o.has("x") ? o.get("x").getAsInt() : 0;
                    int y = o.has("y") ? o.get("y").getAsInt() : 80;
                    int z = o.has("z") ? o.get("z").getAsInt() : 0;
                    return new Spawn(dim, x, y, z);
                }
            })
            .create();

    private EvolutionBoostConfig() {}

    public static EvolutionBoostConfig get() {
        EvolutionBoostConfig i = INSTANCE;
        return i != null ? i : loadOrCreate();
    }

    public static synchronized EvolutionBoostConfig loadOrCreate() {
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("evolutionboost.json");

            if (Files.exists(file)) {
                try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    INSTANCE = GSON.fromJson(br, EvolutionBoostConfig.class);
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

        // Fallbacks für alte Configs ohne neue Felder
        if (INSTANCE.maxBoostMultiplier <= 0) {
            INSTANCE.maxBoostMultiplier = 10.0;
        }
        if (INSTANCE.shinyBaseOdds <= 0) {
            INSTANCE.shinyBaseOdds = 8192;
        }
        if (INSTANCE.shinyCharmMultiplier <= 0) {
            INSTANCE.shinyCharmMultiplier = 2.0;
        }
        if (INSTANCE.shinyCharmRadius <= 0) {
            INSTANCE.shinyCharmRadius = 64.0;
        }
        if (INSTANCE.dimensionBoosts == null) {
            INSTANCE.dimensionBoosts = new LinkedHashMap<>();
        }
        if (INSTANCE.eventSpawns == null) {
            INSTANCE.eventSpawns = new LinkedHashMap<>();
        }
        if (INSTANCE.christmasBaseMultiplier <= 0) {
            INSTANCE.christmasBaseMultiplier = 1.5;
        }
        if (INSTANCE.christmasStormMultiplier <= 0) {
            INSTANCE.christmasStormMultiplier = 2.0;
        }
        if (INSTANCE.christmasStormEveryMinutes <= 0) {
            INSTANCE.christmasStormEveryMinutes = 60;
        }
        if (INSTANCE.christmasStormDurationMinutes <= 0) {
            INSTANCE.christmasStormDurationMinutes = 6;
        }

        return INSTANCE;
    }

    public static synchronized void save() {
        if (INSTANCE == null) return;
        try {
            Path dir = Path.of("config", "evolutionboost");
            Files.createDirectories(dir);
            Path file = dir.resolve("evolutionboost.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception ignored) {}
    }

    private static EvolutionBoostConfig defaults() {
        EvolutionBoostConfig c = new EvolutionBoostConfig();

        // General Settings
        c.maxBoostMultiplier = 100.0;
        c.shinyBaseOdds = 8192;

        // Shiny Charm
        c.shinyCharmEnabled = true;
        c.shinyCharmMultiplier = 2.0;
        c.shinyCharmRadius = 64.0;

        // Maps
        c.dimensionBoosts = new LinkedHashMap<>();
        c.eventSpawns = new LinkedHashMap<>();

        // Christmas Settings
        c.christmasBaseMultiplier = 1.5;
        c.christmasStormMultiplier = 2.0;
        c.christmasStormEveryMinutes = 60;
        c.christmasStormDurationMinutes = 6;

        return c;
    }

    public Spawn getSpawn(String key) { return eventSpawns.get(key); }
    public void putSpawn(String key, Spawn spawn) { eventSpawns.put(key, spawn); }
}