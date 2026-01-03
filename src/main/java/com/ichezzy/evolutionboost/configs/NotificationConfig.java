package com.ichezzy.evolutionboost.configs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwaltet Notification-Einstellungen pro Spieler.
 * 
 * Speicherort: <world>/evolutionboost/notifications.json
 */
public final class NotificationConfig {
    private NotificationConfig() {}

    private static final Map<UUID, PlayerNotificationSettings> SETTINGS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PLAYER_NAMES = new ConcurrentHashMap<>();
    private static MinecraftServer SERVER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void init(MinecraftServer server) {
        SERVER = server;
        loadData();
        EvolutionBoost.LOGGER.info("[notifications] NotificationConfig initialized with {} player entries.", SETTINGS.size());
    }

    public static void shutdown() {
        saveData();
    }

    // ==================== Public API ====================

    public static boolean isEnabled(UUID uuid, NotificationType type) {
        PlayerNotificationSettings settings = SETTINGS.get(uuid);
        if (settings == null) return true; // Default: an
        return settings.isEnabled(type);
    }

    public static void setEnabled(UUID uuid, String playerName, NotificationType type, boolean enabled) {
        PlayerNotificationSettings settings = SETTINGS.computeIfAbsent(uuid, k -> new PlayerNotificationSettings());
        settings.setEnabled(type, enabled);
        PLAYER_NAMES.put(uuid, playerName);
        saveData();
    }

    public static void setAllEnabled(UUID uuid, String playerName, boolean enabled) {
        PlayerNotificationSettings settings = SETTINGS.computeIfAbsent(uuid, k -> new PlayerNotificationSettings());
        for (NotificationType type : NotificationType.values()) {
            settings.setEnabled(type, enabled);
        }
        PLAYER_NAMES.put(uuid, playerName);
        saveData();
    }

    public static Map<NotificationType, Boolean> getAllSettings(UUID uuid) {
        PlayerNotificationSettings settings = SETTINGS.get(uuid);
        Map<NotificationType, Boolean> result = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            result.put(type, settings == null || settings.isEnabled(type));
        }
        return result;
    }

    // ==================== Notification Types ====================

    public enum NotificationType {
        REWARDS("rewards", "Reward notifications"),
        DEX("dex", "Pokédex milestone notifications"),
        QUESTS("quests", "Quest notifications");

        private final String id;
        private final String description;

        NotificationType(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() { return id; }
        public String getDescription() { return description; }

        public static NotificationType fromId(String id) {
            for (NotificationType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    // ==================== Data Classes ====================

    private static class PlayerNotificationSettings {
        boolean rewards = true;
        boolean dex = true;
        boolean quests = true;

        boolean isEnabled(NotificationType type) {
            return switch (type) {
                case REWARDS -> rewards;
                case DEX -> dex;
                case QUESTS -> quests;
            };
        }

        void setEnabled(NotificationType type, boolean enabled) {
            switch (type) {
                case REWARDS -> rewards = enabled;
                case DEX -> dex = enabled;
                case QUESTS -> quests = enabled;
            }
        }
    }

    // ==================== Persistence ====================

    private static Path getDataFile() {
        if (SERVER == null) {
            return Path.of("config", "evolutionboost", "notifications.json");
        }
        Path root = SERVER.getWorldPath(LevelResource.ROOT);
        Path sub = root.resolve("evolutionboost");
        try { Files.createDirectories(sub); } catch (Exception ignored) {}
        return sub.resolve("notifications.json");
    }

    private static void loadData() {
        Path file = getDataFile();
        if (!Files.exists(file)) return;

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            NotificationDataFile loaded = GSON.fromJson(br, NotificationDataFile.class);
            if (loaded != null && loaded.players != null) {
                SETTINGS.clear();
                PLAYER_NAMES.clear();

                for (NotificationDataFile.PlayerEntry entry : loaded.players) {
                    try {
                        UUID uuid = UUID.fromString(entry.uuid);
                        PlayerNotificationSettings settings = new PlayerNotificationSettings();
                        settings.rewards = entry.rewards;
                        settings.dex = entry.dex;
                        settings.quests = entry.quests;
                        SETTINGS.put(uuid, settings);
                        if (entry.name != null) {
                            PLAYER_NAMES.put(uuid, entry.name);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[notifications] Failed to load: {}", e.getMessage());
        }
    }

    private static void saveData() {
        try {
            Path file = getDataFile();
            Files.createDirectories(file.getParent());

            NotificationDataFile out = new NotificationDataFile();
            out.players = new ArrayList<>();

            for (Map.Entry<UUID, PlayerNotificationSettings> entry : SETTINGS.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerNotificationSettings settings = entry.getValue();

                NotificationDataFile.PlayerEntry pe = new NotificationDataFile.PlayerEntry();
                pe.uuid = uuid.toString();
                pe.name = PLAYER_NAMES.getOrDefault(uuid, "Unknown");
                pe.rewards = settings.rewards;
                pe.dex = settings.dex;
                pe.quests = settings.quests;

                out.players.add(pe);
            }

            out.players.sort(Comparator.comparing(p -> p.name != null ? p.name : ""));

            try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(out, bw);
            }
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[notifications] Failed to save: {}", e.getMessage());
        }
    }

    private static class NotificationDataFile {
        List<PlayerEntry> players = new ArrayList<>();

        static class PlayerEntry {
            String uuid;
            String name;
            boolean rewards = true;
            boolean dex = true;
            boolean quests = true;
        }
    }
}
