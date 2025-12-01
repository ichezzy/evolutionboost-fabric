package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Verwalten aktiver Teleport-Sessions (Ticket-basiert ODER manuell via /eventtp).
 *  Spawns werden in EvolutionBoostConfig (/config/evolutionboost/main.json) unter eventSpawns gespeichert. */
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
        public long ticksLeft;          // Countdown (bei Ticket) oder Long.MAX_VALUE (manuell)
        public final boolean manual;    // true = /eventtp (kein Adventure, kein Countdown)

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

    /* ================= Spawns aus EvolutionBoostConfig ================= */

    private static final Map<Target, BlockPos> SPAWNS = new ConcurrentHashMap<>();

    /** Standard-Spawn, falls nichts konfiguriert ist. */
    private static BlockPos defaultSpawn(Target t) {
        // Standard: (0,23,0) für alle
        return new BlockPos(0, 23, 0);
    }

    /** Convenience: aktuelle Config-Instanz. */
    private static EvolutionBoostConfig cfg() {
        return EvolutionBoostConfig.get();
    }

    /**
     * Spawns aus EvolutionBoostConfig in den RAM-Cache laden.
     * Keys: "halloween", "safari", "christmas". Fällt auf Defaults zurück.
     */
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

    /**
     * Einen Spawn in die Config schreiben (inkl. Dimension zum Ziel) und speichern.
     */
    private static void saveSpawnToConfig(Target t, BlockPos pos) {
        String dimStr = t.dim.location().toString(); // z.B. "event:halloween"
        cfg().putSpawn(t.key(), new EvolutionBoostConfig.Spawn(dimStr, pos.getX(), pos.getY(), pos.getZ()));
        EvolutionBoostConfig.save();
    }

    /**
     * Migration: alte /config/evolutionboost/event_spawns.json (falls vorhanden)
     * nach main.json übernehmen und Legacy-Datei löschen.
     * (christmas gibt es dort naturgemäß nicht – ist ok)
     */
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
            EvolutionBoost.LOGGER.info("[eventtp] Migrated legacy event_spawns.json -> main.json and removed legacy file.");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.warn("[eventtp] failed to migrate legacy event_spawns.json: {}", e.getMessage());
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
        // 1) Legacy-Migration (falls alte Datei vorhanden)
        migrateLegacyEventSpawnsIfPresent();
        // 2) Spawns aus Haupt-Config laden
        loadSpawnsFromConfig();

        // Tick-Loop für Sessions
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Iterator<Session> it = ACTIVE.values().iterator();
            while (it.hasNext()) {
                Session sess = it.next();
                ServerPlayer p = s.getPlayerList().getPlayer(sess.uuid);
                if (p == null) continue;

                // Falls Spieler Dimension verlassen hat -> sofort resetten
                if (!p.serverLevel().dimension().equals(sess.target.dim)) {
                    restore(s, p, sess);
                    it.remove();
                    continue;
                }

                if (!sess.manual) {
                    // Ticket-Countdown
                    if (--sess.ticksLeft <= 0) {
                        restore(s, p, sess);
                        it.remove();
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
                            String txt = String.format("[Event Timer] %02d:%02d", mm, ss);
                            p.displayClientMessage(Component.literal(txt).withStyle(ChatFormatting.GOLD), true);
                        }
                    }
                }
            }
        });
    }

    public static boolean hasSession(UUID id) { return ACTIVE.containsKey(id); }

    public static BlockPos getSpawn(Target t) {
        return SPAWNS.getOrDefault(t, defaultSpawn(t));
    }

    /** Persistiert jetzt in /config/evolutionboost/main.json -> eventSpawns. */
    public static void setSpawn(Target t, BlockPos pos) {
        SPAWNS.put(t, pos.immutable());
        saveSpawnToConfig(t, pos.immutable());
    }

    /** Ticket: Countdown + Adventure + Auto-Return. */
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
        return true;
    }

    /** Manuell: kein Adventure, kein Countdown. Rückkehr via /eventtp return. */
    public static boolean startManual(ServerPlayer p, Target target) {
        if (hasSession(p.getUUID())) return false;

        var retDim = p.serverLevel().dimension();
        double rx = p.getX(), ry = p.getY(), rz = p.getZ();
        float yaw = p.getYRot(), pitch = p.getXRot();
        GameType prev = p.gameMode.getGameModeForPlayer();

        ServerLevel dst = p.server.getLevel(target.dim);
        if (dst == null) return false;

        BlockPos tp = getSpawn(target);
        p.teleportTo(dst, tp.getX() + 0.5, tp.getY(), tp.getZ() + 0.5, yaw, pitch);
        // kein Adventure, Session ohne Ablauf
        ACTIVE.put(p.getUUID(), new Session(p.getUUID(), target, retDim, rx, ry, rz, yaw, pitch, prev, Long.MAX_VALUE, true));
        return true;
    }

    /** Für /eventtp return (manuell) oder Force-Return. */
    public static boolean returnNow(ServerPlayer p) {
        Session sess = ACTIVE.remove(p.getUUID());
        if (sess == null) return false;
        restore(p.server, p, sess);
        return true;
    }

    private static void restore(MinecraftServer server, ServerPlayer p, Session sess) {
        ServerLevel back = server.getLevel(sess.returnDim);
        if (back == null) back = server.overworld();
        p.teleportTo(back, sess.rx, sess.ry, sess.rz, sess.ryaw, sess.rpitch);
        p.setGameMode(sess.previousMode);
    }
}
