package com.ichezzy.evolutionboost.boost;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Holds active boosts (global + per-player) and persists them. */
public class BoostManager extends SavedData {
    private static final String SAVE_KEY = EvolutionBoost.MOD_ID + "_boosts";

    private final Map<String, ActiveBoost> active = new ConcurrentHashMap<>();
    private final Map<String, ServerBossEvent> bossbars = new HashMap<>();

    /** 1.21.1 way: use a Factory + computeIfAbsent(factory, key). */
    public static BoostManager get(MinecraftServer server) {
        var level = server.overworld();
        if (level == null) throw new IllegalStateException("[evolutionboost] Overworld not ready yet");
        var storage = level.getDataStorage();
        var factory = new SavedData.Factory<>(
                BoostManager::new,                 // constructor for new
                BoostManager::load,                // reader from NBT
                null                               // DataFixTypes (none)
        );
        return storage.computeIfAbsent(factory, SAVE_KEY);
    }

    public BoostManager() {}

    // ---------- Persistence ----------
    public static BoostManager load(CompoundTag tag, HolderLookup.Provider lookup) {
        BoostManager m = new BoostManager();
        ListTag list = tag.getList("boosts", 10); // 10 = Compound
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            BoostType type = BoostType.valueOf(t.getString("type"));
            BoostScope scope = BoostScope.valueOf(t.getString("scope"));
            double mult = t.getDouble("mult");
            long duration = t.getLong("duration");
            UUID player = t.contains("player") ? UUID.fromString(t.getString("player")) : null;
            String key = t.getString("key");
            String pname = t.contains("pname") ? t.getString("pname") : null;

            ActiveBoost ab = new ActiveBoost(type, scope, mult, duration, player, pname);
            ab.bossBarId = key;
            m.active.put(key, ab);
        }
        return m;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag list = new ListTag();
        for (var e : active.entrySet()) {
            ActiveBoost ab = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putString("type", ab.type.name());
            t.putString("scope", ab.scope.name());
            t.putDouble("mult", ab.multiplier);
            t.putLong("start", ab.startTimeMs);
            t.putLong("duration", ab.durationMs);
            if (ab.player != null) t.putString("player", ab.player.toString());
            if (ab.playerName != null) t.putString("pname", ab.playerName);
            t.putString("key", e.getKey());
            list.add(t);
        }
        tag.put("boosts", list);
        return tag;
    }

    // ---------- API ----------
    public String addBoost(MinecraftServer server, ActiveBoost ab) {
        String key = makeKey(ab);
        ab.bossBarId = key;
        active.put(key, ab);
        createOrUpdateBossbar(server, ab);
        setDirty();
        return key;
    }

    public int clearGlobal(BoostType typeOrNull) {
        var keys = active.entrySet().stream()
                .filter(e -> e.getValue().scope == BoostScope.GLOBAL)
                .filter(e -> typeOrNull == null || e.getValue().type == typeOrNull)
                .map(Map.Entry::getKey).collect(Collectors.toList());
        keys.forEach(this::removeActiveAndBar);
        return keys.size();
    }

    public int clearPlayer(UUID player, BoostType typeOrNull) {
        var keys = active.entrySet().stream()
                .filter(e -> e.getValue().scope == BoostScope.PLAYER)
                .filter(e -> Objects.equals(e.getValue().player, player))
                .filter(e -> typeOrNull == null || e.getValue().type == typeOrNull)
                .map(Map.Entry::getKey).collect(Collectors.toList());
        keys.forEach(this::removeActiveAndBar);
        return keys.size();
    }

    public int clearAll() {
        var keys = new ArrayList<>(active.keySet());
        keys.forEach(this::removeActiveAndBar);
        return keys.size();
    }

    private void removeActiveAndBar(String key) {
        active.remove(key);
        removeBossbar(key);
        setDirty();
    }

    public double getMultiplierFor(BoostType type, UUID playerOrNull) {
        long now = System.currentTimeMillis();
        double mult = 1.0;
        for (ActiveBoost ab : active.values()) {
            if (ab.type != type) continue;
            if (ab.endTimeMs < now) continue;
            if (ab.scope == BoostScope.GLOBAL) mult *= ab.multiplier;
            else if (playerOrNull != null && playerOrNull.equals(ab.player)) mult *= ab.multiplier;
        }
        return mult;
    }

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        for (var entry : active.entrySet()) {
            String key = entry.getKey();
            ActiveBoost ab = entry.getValue();
            long left = ab.millisLeft(now);
            if (left <= 0) { toRemove.add(key); continue; }
            updateBossbar(server, ab, left);
        }
        toRemove.forEach(this::removeActiveAndBar);
    }

    private static String makeKey(ActiveBoost ab) {
        if (ab.scope == BoostScope.GLOBAL) return ab.type.name() + "@GLOBAL";
        return ab.type.name() + "@" + ab.player;
    }

    private void createOrUpdateBossbar(MinecraftServer server, ActiveBoost ab) {
        var color = BoostColors.color(ab.type);
        var overlay = BossEvent.BossBarOverlay.PROGRESS;
        ServerBossEvent bar = bossbars.get(ab.bossBarId);
        if (bar == null) {
            bar = new ServerBossEvent(titleFor(ab, ab.durationMs), color, overlay);
            bossbars.put(ab.bossBarId, bar);
        }
        if (ab.scope == BoostScope.GLOBAL) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) bar.addPlayer(sp);
        } else if (ab.player != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(ab.player);
            if (sp != null) bar.addPlayer(sp);
        }
        updateBossbar(server, ab, ab.millisLeft(System.currentTimeMillis()));
    }

    private void updateBossbar(MinecraftServer server, ActiveBoost ab, long leftMs) {
        ServerBossEvent bar = bossbars.get(ab.bossBarId);
        if (bar == null) return;
        float progress = Math.max(0f, Math.min(1f, (float) leftMs / (float) ab.durationMs));
        bar.setProgress(progress);
        bar.setName(titleFor(ab, leftMs));
        // keep viewers in sync
        if (ab.scope == BoostScope.GLOBAL) {
            for (ServerPlayer sp : server.getPlayerList().getPlayers())
                if (!bar.getPlayers().contains(sp)) bar.addPlayer(sp);
        } else if (ab.player != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(ab.player);
            if (sp != null && !bar.getPlayers().contains(sp)) bar.addPlayer(sp);
        }
    }

    private void removeBossbar(String key) {
        ServerBossEvent bar = bossbars.remove(key);
        if (bar != null) {
            for (ServerPlayer sp : new ArrayList<>(bar.getPlayers())) {
                bar.removePlayer(sp);
            }
        }
    }

    private Component titleFor(ActiveBoost ab, long leftMs) {
        String scope = ab.scope == BoostScope.GLOBAL ? "GLOBAL" : "PLAYER";
        String who = ab.scope == BoostScope.PLAYER
                ? " (" + (ab.playerName != null ? ab.playerName : String.valueOf(ab.player)) + ")"
                : "";
        String timer = formatDuration(leftMs);
        String txt = scope + " " + ab.type + " BOOST x" + ab.multiplier + " " + timer + who;

        // farbe + fett nach Typ
        var color = switch (ab.type) {
            case SHINY -> ChatFormatting.GOLD;
            case XP    -> ChatFormatting.GREEN;
            case DROP  -> ChatFormatting.BLUE;
            case IV    -> ChatFormatting.DARK_PURPLE;
        };
        return Component.literal(txt).setStyle(Style.EMPTY.withColor(color).withBold(true));
    }

    private static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86_400; s %= 86_400;
        long h = s / 3600;   s %= 3600;
        long m = s / 60;     s %= 60;
        if (d > 0) return String.format("(%dd %02dh %02dm)", d, h, m);
        if (h > 0) return String.format("(%dh %02dm %02ds)", h, m, s);
        if (m > 0) return String.format("(%dm %02ds)", m, s);
        return String.format("(%ds)", s);
    }
}
