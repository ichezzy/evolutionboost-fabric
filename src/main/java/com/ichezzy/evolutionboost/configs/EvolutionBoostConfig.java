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

    /** Globale Beispiel-Settings */
    public double maxBoostMultiplier = 10.0;
    public boolean shinyCharmEnabled = false;
    public int shinyCharmTargetOdds = 2048;

    /** NEU: Christmas-Event Settings */
    public boolean christmasDebug = false;
    /** Ein Blizzard pro ... Minuten (z. B. 60) */
    public int christmasStormEveryMinutes = 60;
    /** Dauer pro Blizzard in Minuten (z. B. 6) */
    public int christmasStormDurationMinutes = 6;
    /** Sichtreduktions-Faktor während Blizzard (nur Info/Servereffekte; Client-Fog optional) */
    public double christmasVisibilityFactor = 0.7;
    /** Dimensionale Multiplikatoren, die NUR während Blizzard gelten: */
    public double christmasXpMultiplierDuringStorm = 2.0;
    public double christmasShinyMultiplierDuringStorm = 1.5;

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
        c.maxBoostMultiplier = 10.0;
        c.shinyCharmEnabled = false;
        c.shinyCharmTargetOdds = 2048;

        // Christmas defaults
        c.christmasDebug = false;
        c.christmasStormEveryMinutes = 60;
        c.christmasStormDurationMinutes = 6;
        c.christmasVisibilityFactor = 0.7;
        c.christmasXpMultiplierDuringStorm = 2.0;
        c.christmasShinyMultiplierDuringStorm = 1.5;

        return c;
    }

    public Spawn getSpawn(String key) { return eventSpawns.get(key); }
    public void putSpawn(String key, Spawn spawn) { eventSpawns.put(key, spawn); }
}
