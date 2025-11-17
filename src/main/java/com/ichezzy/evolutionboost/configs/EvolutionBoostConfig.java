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
 * Globale EvolutionBoost-Config (main.json)
 * Pfad: /config/evolutionboost/main.json
 *
 * Beinhaltet u. a. Event-Spawns (z. B. "halloween", "safari") und Basiseinstellungen.
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

    /** z. B. "halloween" -> Spawn, "safari" -> Spawn */
    public Map<String, Spawn> eventSpawns = new LinkedHashMap<>();

    /** Basiseinstellungen */
    public double  maxBoostMultiplier   = 10.0;
    public boolean shinyCharmEnabled    = false;
    public int     shinyCharmTargetOdds = 2048;

    /** NEU: Debug für Halloween-TimeLock in Haupt-Config */
    public boolean halloweenDebug = false;

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
            Path file = dir.resolve("main.json");

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
            Path file = dir.resolve("main.json");
            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, bw);
            }
        } catch (Exception ignored) {}
    }

    private static EvolutionBoostConfig defaults() {
        EvolutionBoostConfig c = new EvolutionBoostConfig();
        // Keine Defaults für Spawns gesetzt – Admin kann mit /eventtp <x> setspawn schreiben.
        c.maxBoostMultiplier = 10.0;
        c.shinyCharmEnabled = false;
        c.shinyCharmTargetOdds = 2048;
        c.halloweenDebug = false;
        return c;
    }

    public Spawn getSpawn(String key) { return eventSpawns.get(key); }
    public void  putSpawn(String key, Spawn spawn) { eventSpawns.put(key, spawn); }
}
