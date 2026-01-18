package com.ichezzy.evolutionboost.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-seitige Konfiguration für EvolutionBoost.
 * Speichert Einstellungen wie HUD-Anzeige lokal auf dem Client.
 */
public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig INSTANCE;

    // ==================== Config Fields ====================

    /** Ob das Boost-HUD angezeigt wird */
    public boolean hudEnabled = true;

    /** HUD X-Position (Pixel vom linken Rand) */
    public int hudX = 16;

    /** HUD Y-Position als Prozent der Bildschirmhöhe (0.0 - 1.0) */
    public double hudYPercent = 0.093;

    // ==================== Singleton ====================

    public static ClientConfig get() {
        if (INSTANCE == null) {
            INSTANCE = loadOrCreate();
        }
        return INSTANCE;
    }

    // ==================== Load / Save ====================

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("evolutionboost")
                .resolve("client.json");
    }

    private static ClientConfig loadOrCreate() {
        Path path = getConfigPath();

        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                ClientConfig loaded = GSON.fromJson(json, ClientConfig.class);
                if (loaded != null) {
                    EvolutionBoost.LOGGER.info("[client-config] Loaded client config");
                    return loaded;
                }
            } catch (IOException e) {
                EvolutionBoost.LOGGER.warn("[client-config] Failed to load client config: {}", e.getMessage());
            }
        }

        // Neue Config erstellen
        ClientConfig config = new ClientConfig();
        config.save();
        return config;
    }

    public void save() {
        Path path = getConfigPath();

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
            EvolutionBoost.LOGGER.debug("[client-config] Saved client config");
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[client-config] Failed to save client config: {}", e.getMessage());
        }
    }

    // ==================== Convenience Methods ====================

    public static boolean isHudEnabled() {
        return get().hudEnabled;
    }

    public static void setHudEnabled(boolean enabled) {
        get().hudEnabled = enabled;
        get().save();
    }

    public static void toggleHud() {
        setHudEnabled(!isHudEnabled());
    }
}
