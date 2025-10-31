package com.ichezzy.evolutionboost.ticket;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Verwalten aktiver Teleport-Sessions (Ticket-basiert ODER manuell via /eventtp). */
public final class TicketManager {
    private TicketManager() {}

    public enum Target {
        HALLOWEEN(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:halloween"))),
        SAFARI(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:safari_zone")));

        public final ResourceKey<Level> dim;
        Target(ResourceKey<Level> d) { this.dim = d; }

        public String key() {
            return this == HALLOWEEN ? "halloween" : "safari";
        }

        public static Target from(String s) {
            if (s == null) return null;
            String k = s.trim().toLowerCase(Locale.ROOT);
            if (k.equals("halloween")) return HALLOWEEN;
            if (k.equals("safari") || k.equals("safari_zone")) return SAFARI;
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

    /* ================= Persistente Spawns ================= */

    private static final Map<Target, BlockPos> SPAWNS = new ConcurrentHashMap<>();
    private static Path configDir() { return FabricLoader.getInstance().getConfigDir().resolve(EvolutionBoost.MOD_ID); }
    private static Path spawnsFile() { return configDir().resolve("event_spawns.json"); }

    /** Defaults, falls keine Datei existiert. */
    private static BlockPos defaultSpawn(Target t) {
        // Beide standardmäßig auf (0,23,0)
        return new BlockPos(0, 23, 0);
    }

    public static BlockPos getSpawn(Target t) {
        return SPAWNS.getOrDefault(t, defaultSpawn(t));
    }

    public static void setSpawn(Target t, BlockPos pos) {
        SPAWNS.put(t, pos.immutable());
        saveSpawns();
    }

    public static void init(MinecraftServer server) {
        loadSpawns();

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

    private static void loadSpawns() {
        SPAWNS.clear();
        try {
            Files.createDirectories(configDir());
            Path f = spawnsFile();
            if (!Files.exists(f)) {
                // Defaults speichern
                SPAWNS.put(Target.HALLOWEEN, defaultSpawn(Target.HALLOWEEN));
                SPAWNS.put(Target.SAFARI, defaultSpawn(Target.SAFARI));
                saveSpawns();
                return;
            }

            // *** FIX: kompletten Dateiinhalt lesen (Reader#transferTo braucht Writer, daher Files.readString) ***
            String text = Files.readString(f);

            parseOne(Target.HALLOWEEN, text, "\"halloween\"");
            parseOne(Target.SAFARI, text, "\"safari\"");
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[eventtp] failed to load spawns: {}", e.getMessage());
            // Fallbacks
            SPAWNS.put(Target.HALLOWEEN, defaultSpawn(Target.HALLOWEEN));
            SPAWNS.put(Target.SAFARI, defaultSpawn(Target.SAFARI));
        }
    }

    private static void parseOne(Target t, String json, String key) {
        int i = json.indexOf(key);
        if (i < 0) { SPAWNS.put(t, defaultSpawn(t)); return; }
        int b = json.indexOf('{', i);
        int e = json.indexOf('}', b);
        if (b < 0 || e < 0) { SPAWNS.put(t, defaultSpawn(t)); return; }
        String obj = json.substring(b + 1, e);
        int x = intField(obj, "\"x\"");
        int y = intField(obj, "\"y\"");
        int z = intField(obj, "\"z\"");
        if (x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
            SPAWNS.put(t, defaultSpawn(t));
            return;
        }
        if (y == Integer.MIN_VALUE) y = 23; // Safety
        SPAWNS.put(t, new BlockPos(x, y, z));
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

    private static void saveSpawns() {
        try {
            Files.createDirectories(configDir());
            Path f = spawnsFile();
            BlockPos h = SPAWNS.getOrDefault(Target.HALLOWEEN, defaultSpawn(Target.HALLOWEEN));
            BlockPos s = SPAWNS.getOrDefault(Target.SAFARI, defaultSpawn(Target.SAFARI));
            String json = """
                    {
                      "halloween": { "x": %d, "y": %d, "z": %d },
                      "safari":    { "x": %d, "y": %d, "z": %d }
                    }
                    """.formatted(h.getX(), h.getY(), h.getZ(), s.getX(), s.getY(), s.getZ());
            try (Writer w = Files.newBufferedWriter(f)) {
                w.write(json);
            }
        } catch (IOException e) {
            EvolutionBoost.LOGGER.warn("[eventtp] failed to save spawns: {}", e.getMessage());
        }
    }

    /* ================= Public API ================= */

    public static boolean hasSession(UUID id) { return ACTIVE.containsKey(id); }

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
