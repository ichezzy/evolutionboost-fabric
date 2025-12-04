package com.ichezzy.evolutionboost.boost;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hält aktive Boosts (nur noch GLOBAL) und persistiert sie.
 * PLUS: Dimension-Multiplikatoren pro BoostType (werden über EvolutionBoostConfig gespeichert).
 */
public class BoostManager extends SavedData {
    private static final String SAVE_KEY = EvolutionBoost.MOD_ID + "_boosts";

    private final Map<String, ActiveBoost> active = new ConcurrentHashMap<>();
    private final Map<String, ServerBossEvent> bossbars = new HashMap<>();

    /** Dimensionale Multiplikatoren pro BoostType. */
    private final EnumMap<BoostType, Map<ResourceKey<Level>, Double>> dimensionMults =
            new EnumMap<>(BoostType.class);

    /** Einmaliger Flag, damit wir Config-Daten nicht jedes Mal neu einlesen. */
    private boolean dimLoadedFromConfig = false;

    /** 1.21.1-Weg: Factory + computeIfAbsent(factory, key). */
    public static BoostManager get(MinecraftServer server) {
        var level = server.overworld();
        if (level == null) throw new IllegalStateException("[evolutionboost] Overworld not ready yet");
        var storage = level.getDataStorage();
        var factory = new SavedData.Factory<>(
                BoostManager::new,
                BoostManager::load,
                null
        );
        BoostManager manager = storage.computeIfAbsent(factory, SAVE_KEY);
        if (!manager.dimLoadedFromConfig) {
            manager.reloadDimensionMultipliersFromConfig();
            manager.dimLoadedFromConfig = true;
        }
        return manager;
    }

    public BoostManager() {
        for (BoostType t : BoostType.values()) {
            dimensionMults.put(t, new ConcurrentHashMap<>());
        }
    }

    // ---------- Persistence (GLOBAL-Boosts) ----------

    public static BoostManager load(CompoundTag tag, HolderLookup.Provider lookup) {
        BoostManager m = new BoostManager();
        ListTag list = tag.getList("boosts", 10); // 10 = Compound
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            BoostType type = BoostType.valueOf(t.getString("type"));
            String scopeRaw = t.getString("scope");
            BoostScope scope = BoostScope.fromPersistent(scopeRaw);
            double mult = t.getDouble("mult");
            long duration = t.getLong("duration");
            String key = t.getString("key");

            ActiveBoost ab = new ActiveBoost(type, scope, mult, duration);
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
            t.putString("key", e.getKey());
            list.add(t);
        }
        tag.put("boosts", list);
        return tag;
    }

    // ---------- API: hinzufügen/entfernen (GLOBAL) ----------

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

    // ---------- Dimension-API (persistent über EvolutionBoostConfig) ----------

    /**
     * Setzt den Multiplikator für (Type, Dimension) und schreibt ihn in EvolutionBoostConfig.dimensionBoosts.
     * multiplier <= 1.0 wird als "kein spezieller Boost" behandelt und aus der Config entfernt.
     */
    public void setDimensionMultiplier(BoostType type, ResourceKey<Level> dim, double multiplier) {
        if (dim == null) return;
        double clamped = Math.max(0.0, multiplier);
        dimensionMults.get(type).put(dim, clamped);

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
        String dimKey = dim.location().toString();
        Map<String, Double> byType = cfg.dimensionBoosts
                .computeIfAbsent(dimKey, k -> new LinkedHashMap<>());

        if (clamped <= 1.0) {
            byType.remove(type.name());
            if (byType.isEmpty()) {
                cfg.dimensionBoosts.remove(dimKey);
            }
        } else {
            byType.put(type.name(), clamped);
        }
        EvolutionBoostConfig.save();
    }

    public void clearDimensionMultiplier(BoostType type, ResourceKey<Level> dim) {
        if (dim == null) return;
        dimensionMults.get(type).remove(dim);

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
        String dimKey = dim.location().toString();
        Map<String, Double> byType = cfg.dimensionBoosts.get(dimKey);
        if (byType != null) {
            byType.remove(type.name());
            if (byType.isEmpty()) {
                cfg.dimensionBoosts.remove(dimKey);
            }
            EvolutionBoostConfig.save();
        }
    }

    public void clearAllDimensionMultipliers(ResourceKey<Level> dim) {
        if (dim == null) return;
        for (BoostType t : BoostType.values()) {
            dimensionMults.get(t).remove(dim);
        }

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
        cfg.dimensionBoosts.remove(dim.location().toString());
        EvolutionBoostConfig.save();
    }

    public double getDimensionMultiplier(BoostType type, ResourceKey<Level> dim) {
        if (dim == null) return 1.0;
        return dimensionMults.get(type).getOrDefault(dim, 1.0);
    }

    /**
     * Lädt alle Dimension-Boosts aus EvolutionBoostConfig.dimensionBoosts in die internen Maps.
     * Wird genau einmal bei get(server) aufgerufen.
     */
    public void reloadDimensionMultipliersFromConfig() {
        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();

        for (BoostType t : BoostType.values()) {
            dimensionMults.get(t).clear();
        }

        if (cfg.dimensionBoosts == null || cfg.dimensionBoosts.isEmpty()) {
            return;
        }

        for (var dimEntry : cfg.dimensionBoosts.entrySet()) {
            String dimKey = dimEntry.getKey();
            ResourceLocation rl;
            try {
                rl = ResourceLocation.parse(dimKey);
            } catch (Exception e) {
                EvolutionBoost.LOGGER.warn("[Boost] Invalid dimension key in config: {}", dimKey);
                continue;
            }
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, rl);
            Map<String, Double> byType = dimEntry.getValue();
            if (byType == null) continue;

            for (var typeEntry : byType.entrySet()) {
                BoostType type;
                try {
                    type = BoostType.valueOf(typeEntry.getKey());
                } catch (IllegalArgumentException ex) {
                    EvolutionBoost.LOGGER.warn("[Boost] Invalid boost type for dimension {}: {}", dimKey, typeEntry.getKey());
                    continue;
                }
                Double val = typeEntry.getValue();
                if (val == null || val <= 0.0) continue;
                dimensionMults.get(type).put(dim, val);
            }
        }
    }

    /** Kompat-Overload – player wird ignoriert. */
    public double getMultiplierFor(BoostType type, java.util.UUID playerOrNull) {
        return getMultiplierFor(type, playerOrNull, null);
    }

    /** GLOBAL × DIMENSION. Player-spezifische Boosts existieren nicht mehr. */
    public double getMultiplierFor(BoostType type, java.util.UUID ignoredPlayer, ResourceKey<Level> dimOrNull) {
        long now = System.currentTimeMillis();
        double mult = 1.0;

        for (ActiveBoost ab : active.values()) {
            if (ab.type != type) continue;
            if (ab.endTimeMs < now) continue;
            // es gibt nur GLOBAL-Boosts
            mult *= ab.multiplier;
        }

        mult *= getDimensionMultiplier(type, dimOrNull);
        return mult;
    }

    // ---------- Tick / Bossbars ----------

    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        for (var entry : active.entrySet()) {
            String key = entry.getKey();
            ActiveBoost ab = entry.getValue();
            long left = ab.millisLeft(now);
            if (left <= 0) {
                toRemove.add(key);
                continue;
            }
            updateBossbar(server, ab, left);
        }
        toRemove.forEach(this::removeActiveAndBar);
    }

    private static String makeKey(ActiveBoost ab) {
        // aktuell maximal ein GLOBAL-Boost pro Typ
        return ab.type.name() + "@GLOBAL";
    }

    private void createOrUpdateBossbar(MinecraftServer server, ActiveBoost ab) {
        var color = BoostColors.color(ab.type);
        var overlay = BossEvent.BossBarOverlay.PROGRESS;
        ServerBossEvent bar = bossbars.get(ab.bossBarId);
        if (bar == null) {
            bar = new ServerBossEvent(titleFor(ab, ab.durationMs), color, overlay);
            bossbars.put(ab.bossBarId, bar);
        }
        // GLOBAL -> alle Spieler
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!bar.getPlayers().contains(sp)) {
                bar.addPlayer(sp);
            }
        }
        updateBossbar(server, ab, ab.millisLeft(System.currentTimeMillis()));
    }

    private void updateBossbar(MinecraftServer server, ActiveBoost ab, long leftMs) {
        ServerBossEvent bar = bossbars.get(ab.bossBarId);
        if (bar == null) return;
        float progress = Math.max(0f, Math.min(1f, (float) leftMs / (float) ab.durationMs));
        bar.setProgress(progress);
        bar.setName(titleFor(ab, leftMs));

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!bar.getPlayers().contains(sp)) {
                bar.addPlayer(sp);
            }
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

    /** Hübscher Titel für die Bossbar (GLOBAL-Boosts). */
    private Component titleFor(ActiveBoost ab, long leftMs) {
        String timer = formatDuration(leftMs);
        String txt = "[EVOLUTIONBOOST] GLOBAL " + ab.type + " x" + ab.multiplier + " " + timer;

        ChatFormatting color = BoostColors.chatColor(ab.type);
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
