package com.ichezzy.evolutionboost.reward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class RewardRoles {
    private RewardRoles() {}

    public static final class RolesFile {
        public Set<String> donators = new HashSet<>();
        public Set<String> gymleaders = new HashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RolesFile ROLES = new RolesFile();

    public static void load(MinecraftServer server) {
        try {
            Path file = configPath(server);
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    RolesFile f = GSON.fromJson(r, RolesFile.class);
                    if (f != null) ROLES = f;
                }
            } else {
                // Erste Default-Datei schreiben
                Files.createDirectories(file.getParent());
                try (Writer w = Files.newBufferedWriter(file)) {
                    GSON.toJson(ROLES, w);
                }
            }
        } catch (Exception ignored) { }
    }

    public static boolean isDonator(String name) {
        return ROLES.donators.contains(sanitize(name));
    }

    public static boolean isGymLeader(String name) {
        return ROLES.gymleaders.contains(sanitize(name));
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static Path configPath(MinecraftServer server) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        return worldRoot.resolve("evolutionboost").resolve("rewards").resolve("roles.json");
    }
}
