package com.ichezzy.evolutionboost.item;

import com.google.gson.*;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verwalten aktiver Teleport-Sessions (Ticket-basiert).
 * Sessions werden persistiert und überleben Server-Restarts.
 * Spawns werden in EvolutionBoostConfig gespeichert.
 */
public final class TicketManager {
    private TicketManager() {}

    public enum Target {
        HALLOWEEN(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:halloween"))),
        SAFARI   (ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:safari_zone"))),
        CHRISTMAS(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:christmas")));

        public final ResourceKey<Level> dim;
        Target(ResourceKey<Level> d) { this.dim = d; }

        public String key() {
            switch (this) {
                case HALLOWEEN: return "halloween";
                case SAFARI:    return "safari";
                case CHRISTMAS: return "christmas";
                default:        return name().toLowerCase(Locale.ROOT);
            }
        }

        public static Target from(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT);
            if (k.equals("halloween"))    return HALLOWEEN;
            if (k.equals("safari") || k.equals("safari_zone")) return SAFARI;
            if (k.equals("christmas"))    return CHRISTMAS;
            return null;
        }
    }

    /** Session speichert Rückkehrdaten. */
    public static final class Session {
        public final UUID uuid;
        public final Target target;
        public final ResourceKey<Level> returnDim;
        public final double rx, ry, rz;
        public final float ryaw, rpitch;
        public final GameType previousMode;
        public long ticksLeft;          // Countdown (bei Ticket)
        public final boolean manual;    // Legacy - nicht mehr verwendet für neue Sessions

        public Session(UUID id, Target t, ResourceKey<Level> retDim,
                       double rx, double ry, double rz, float yaw, float pitch, GameType prev,
                       long ticks, boolean manual) {
            this.uuid = id;
            this.target = t;
            this.returnDim = retDim;
            this.rx = rx; this.ry = ry; this.rz = rz;
            this.ryaw = yaw; this.rpitch = pitch;
            this.previousMode = prev;
            this.ticksLeft = ticks;
            this.manual = manual;
        }
    }

    private static final Map<UUID, Session> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Target, BlockPos> SPAWNS = new ConcurrentHashMap<>();

    // Pfad zur Session-Persistierung
    private static Path getSessionFile() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve(EvolutionBoost.MOD_ID)
                .resolve("ticket_sessions.json");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /* ================= Session Persistierung ================= */

    /**
     * Speichert alle aktiven Sessions in eine JSON-Datei.
     */
    public static synchronized void saveSessions() {
        if (ACTIVE.isEmpty()) {
            // Keine Sessions -> Datei löschen falls vorhanden
            try {
                Files.deleteIfExists(getSessionFile());
            } catch (IOException ignored) {}
            return;
        }

        JsonObject root = new JsonObject();
        JsonObject sessions = new JsonObject();

        for (var entry : ACTIVE.entrySet()) {
            Session s = entry.getValue();
            JsonObject sObj = new JsonObject();
            sObj.addProperty("target", s.target.name());
            sObj.addProperty("returnDim", s.returnDim.location().toString());
            sObj.addProperty("rx", s.rx);
            sObj.addProperty("ry", s.ry);
            sObj.addProperty("rz", s.rz);
            sObj.addProperty("ryaw", s.ryaw);
            sObj.addProperty("rpitch", s.rpitch);
            sObj.addProperty("previousMode", s.previousMode.name());
            sObj.addProperty("ticksLeft", s.ticksLeft);
            sObj.addProperty("manual", s.manual);
            sessions.add(entry.getKey().toString(), sObj);
        }

        root.add("sessions", sessions);

        try {
            Path file = getSessionFile();
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            EvolutionBoost.LOGGER.debug("[TicketManager] Saved {} sessions to disk.", ACTIVE.size());
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[TicketManager] Failed to save sessions: {}", e.getMessage());
        }
    }

    /**
     * Lädt Sessions aus der JSON-Datei.
     */
    public static synchronized void loadSessions() {
        Path file = getSessionFile();
        if (!Files.exists(file)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject sessions = root.getAsJsonObject("sessions");

            if (sessions == null) return;

            int loaded = 0;
            for (var entry : sessions.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonObject sObj = entry.getValue().getAsJsonObject();

                    Target target = Target.valueOf(sObj.get("target").getAsString());
                    ResourceLocation dimLoc = ResourceLocation.parse(sObj.get("returnDim").getAsString());
                    ResourceKey<Level> returnDim = ResourceKey.create(Registries.DIMENSION, dimLoc);

                    double rx = sObj.get("rx").getAsDouble();
                    double ry = sObj.get("ry").getAsDouble();
                    double rz = sObj.get("rz").getAsDouble();
                    float ryaw = sObj.get("ryaw").getAsFloat();
                    float rpitch = sObj.get("rpitch").getAsFloat();
                    GameType prevMode = GameType.valueOf(sObj.get("previousMode").getAsString());
                    long ticksLeft = sObj.get("ticksLeft").getAsLong();
                    boolean manual = sObj.has("manual") && sObj.get("manual").getAsBoolean();

                    Session session = new Session(uuid, target, returnDim, rx, ry, rz, ryaw, rpitch, prevMode, ticksLeft, manual);
                    ACTIVE.put(uuid, session);
                    loaded++;
                } catch (Exception e) {
                    EvolutionBoost.LOGGER.warn("[TicketManager] Failed to load session {}: {}", entry.getKey(), e.getMessage());
                }
            }

            EvolutionBoost.LOGGER.info("[TicketManager] Loaded {} sessions from disk.", loaded);
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[TicketManager] Failed to load sessions file: {}", e.getMessage());
        }
    }

    /* ================= Spawns aus EvolutionBoostConfig ================= */

    private static BlockPos defaultSpawn(Target t) {
        return new BlockPos(0, 23, 0);
    }

    private static EvolutionBoostConfig cfg() {
        return EvolutionBoostConfig.get();
    }

    private static void loadSpawnsFromConfig() {
        SPAWNS.clear();
        for (Target t : Target.values()) {
            EvolutionBoostConfig.Spawn s = cfg().getSpawn(t.key());
            if (s != null) {
                SPAWNS.put(t, s.toBlockPos());
            } else {
                SPAWNS.put(t, defaultSpawn(t));
            }
        }
    }

    private static void saveSpawnToConfig(Target t, BlockPos pos) {
        String dimStr = t.dim.location().toString();
        cfg().putSpawn(t.key(), new EvolutionBoostConfig.Spawn(dimStr, pos.getX(), pos.getY(), pos.getZ()));
        EvolutionBoostConfig.save();
    }

    private static void migrateLegacyEventSpawnsIfPresent() {
        Path legacyFile = FabricLoader.getInstance()
                .getConfigDir().resolve(EvolutionBoost.MOD_ID).resolve("event_spawns.json");
        if (!Files.exists(legacyFile)) return;

        try {
            String text = Files.readString(legacyFile);

            BlockPos h = extractPos(text, "\"halloween\"");
            BlockPos s = extractPos(text, "\"safari\"");

            if (h != null) {
                cfg().putSpawn(Target.HALLOWEEN.key(),
                        new EvolutionBoostConfig.Spawn(Target.HALLOWEEN.dim.location().toString(), h.getX(), h.getY(), h.getZ()));
            }
            if (s != null) {
                cfg().putSpawn(Target.SAFARI.key(),
                        new EvolutionBoostConfig.Spawn(Target.SAFARI.dim.location().toString(), s.getX(), s.getY(), s.getZ()));
            }
            EvolutionBoostConfig.save();

            try { Files.deleteIfExists(legacyFile); } catch (IOException ignored) {}
            EvolutionBoost.LOGGER.info("[TicketManager] Migrated legacy event_spawns.json -> main.json and removed legacy file.");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[TicketManager] Failed to migrate legacy event_spawns.json: {}", e.getMessage());
        }
    }

    private static BlockPos extractPos(String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) return null;
        int b = json.indexOf('{', i);
        int e = json.indexOf('}', b);
        if (b < 0 || e < 0) return null;
        String obj = json.substring(b + 1, e);
        int x = intField(obj, "\"x\"");
        int y = intField(obj, "\"y\"");
        int z = intField(obj, "\"z\"");
        if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) return null;
        if (y == Integer.MIN_VALUE) y = 23;
        return new BlockPos(x, y, z);
    }

    private static int intField(String obj, String name) {
        int i = obj.indexOf(name);
        if (i < 0) return Integer.MIN_VALUE;
        int c = obj.indexOf(':', i);
        if (c < 0) return Integer.MIN_VALUE;
        int comma = obj.indexOf(',', c + 1);
        String raw = (comma < 0 ? obj.substring(c + 1) : obj.substring(c + 1, comma)).trim();
        try { return Integer.parseInt(raw); } catch (Exception e) { return Integer.MIN_VALUE; }
    }

    /* ================= Public API ================= */

    public static void init(MinecraftServer server) {
        // 1) Legacy-Migration
        migrateLegacyEventSpawnsIfPresent();
        // 2) Spawns aus Config laden
        loadSpawnsFromConfig();
        // 3) Sessions aus Datei laden
        loadSessions();

        // Tick-Loop für Sessions
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Iterator<Session> it = ACTIVE.values().iterator();
            boolean changed = false;

            while (it.hasNext()) {
                Session sess = it.next();
                ServerPlayer p = s.getPlayerList().getPlayer(sess.uuid);

                // Spieler offline -> Session bleibt erhalten, Timer pausiert
                if (p == null) continue;

                // Falls Spieler Dimension verlassen hat -> sofort resetten
                if (!p.serverLevel().dimension().equals(sess.target.dim)) {
                    restore(s, p, sess);
                    it.remove();
                    changed = true;
                    continue;
                }

                if (!sess.manual) {
                    // Ticket-Countdown
                    if (--sess.ticksLeft <= 0) {
                        restore(s, p, sess);
                        it.remove();
                        changed = true;
                    } else {
                        // Safety: während Ticket-Session Adventure erzwingen
                        if (p.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                            p.setGameMode(GameType.ADVENTURE);
                        }
                        // Goldener Countdown im Actionbar einmal pro Sekunde
                        if (sess.ticksLeft % 20 == 0) {
                            long sec = sess.ticksLeft / 20L;
                            long mm = sec / 60L;
                            long ss = sec % 60L;
                            String timerLabel = getTimerLabel(sess.target);
                            String txt = String.format("[%s] %02d:%02d", timerLabel, mm, ss);
                            p.displayClientMessage(Component.literal(txt).withStyle(ChatFormatting.GOLD), true);
                        }
                    }
                }
            }

            // Sessions speichern wenn sich etwas geändert hat
            if (changed) {
                saveSessions();
            }
        });

        // Bei Server-Stop Sessions speichern
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            saveSessions();
            EvolutionBoost.LOGGER.info("[TicketManager] Saved sessions on server stop.");
        });
    }

    public static boolean hasSession(UUID id) { return ACTIVE.containsKey(id); }

    public static BlockPos getSpawn(Target t) {
        return SPAWNS.getOrDefault(t, defaultSpawn(t));
    }

    public static void setSpawn(Target t, BlockPos pos) {
        SPAWNS.put(t, pos.immutable());
        saveSpawnToConfig(t, pos.immutable());
    }

    /** Ticket: Countdown + Adventure + Auto-Return. Sessions werden persistiert. */
    public static boolean startTicket(ServerPlayer p, Target target, long ticks) {
        if (hasSession(p.getUUID())) return false;

        var retDim = p.serverLevel().dimension();
        double rx = p.getX(), ry = p.getY(), rz = p.getZ();
        float yaw = p.getYRot(), pitch = p.getXRot();
        GameType prev = p.gameMode.getGameModeForPlayer();

        ServerLevel dst = p.server.getLevel(target.dim);
        if (dst == null) return false;

        BlockPos tp = getSpawn(target);
        p.teleportTo(dst, tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5, yaw, pitch);
        p.setGameMode(GameType.ADVENTURE);

        ACTIVE.put(p.getUUID(), new Session(p.getUUID(), target, retDim, rx, ry, rz, yaw, pitch, prev, ticks, false));
        saveSessions(); // Sofort speichern
        return true;
    }

    /** Für Force-Return (z.B. wenn Spieler die Dimension anders verlässt). */
    public static boolean returnNow(ServerPlayer p) {
        Session sess = ACTIVE.remove(p.getUUID());
        if (sess == null) return false;
        restore(p.server, p, sess);
        saveSessions(); // Sofort speichern
        return true;
    }

    private static void restore(MinecraftServer server, ServerPlayer p, Session sess) {
        ServerLevel back = server.getLevel(sess.returnDim);
        if (back == null) back = server.overworld();
        p.teleportTo(back, sess.rx, sess.ry, sess.rz, sess.ryaw, sess.rpitch);
        p.setGameMode(sess.previousMode);
    }

    /**
     * Gibt den Timer-Label für eine bestimmte Ziel-Dimension zurück.
     */
    private static String getTimerLabel(Target target) {
        return switch (target) {
            case SAFARI -> "Time left in the Safari Zone";
            case HALLOWEEN -> "Time left in the Halloween Event";
            case CHRISTMAS -> "Time left in the Christmas Event";
        };
    }

    /**
     * Gibt die aktive Session für einen Spieler zurück.
     */
    public static Session getSession(UUID id) {
        return ACTIVE.get(id);
    }

    /**
     * Beendet eine Session für einen Spieler vorzeitig.
     * @return true wenn erfolgreich, false wenn keine Session existiert
     */
    public static boolean endSessionEarly(ServerPlayer player) {
        Session sess = ACTIVE.remove(player.getUUID());
        if (sess == null) return false;
        restore(player.server, player, sess);
        saveSessions();
        return true;
    }
}