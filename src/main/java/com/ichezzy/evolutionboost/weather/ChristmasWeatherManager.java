package com.ichezzy.evolutionboost.weather;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.configs.EventConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ChristmasWeatherManager {

    private ChristmasWeatherManager() {}

    /** Christmas-Dimension */
    private static final ResourceKey<Level> CHRISTMAS_DIM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("event:christmas"));

    /** Boost-Typen die vom Christmas-System betroffen sind */
    private static final BoostType[] CHRISTMAS_BOOST_TYPES = { BoostType.SHINY, BoostType.XP, BoostType.IV };

    private enum StormState {
        IDLE,      // kein Sturm, warte auf nächsten
        PREPARE,   // 30 Sekunden Vorwarnung
        ACTIVE     // Sturm aktiv (10 Minuten)
    }

    private static StormState christmasState = StormState.IDLE;
    private static int christmasTicks = 0;
    private static int ticksSinceLastStorm = 0;
    private static boolean christmasBoostsInitialized = false;

    // Timing - wird aus Config geladen
    private static final int PREPARE_TICKS = 20 * 30;       // 30 Sekunden Vorwarnung

    private static final int EFFECT_INTERVAL = 20 * 3;      // Alle 3 Sekunden Effekte (Performance-optimiert)
    private static final int DAMAGE_INTERVAL = 20 * 3;      // Alle 3 Sekunden Schaden wenn voll eingefroren

    // Bossbar für den Sturm
    private static ServerBossEvent stormBossbar = null;

    private static final Random RANDOM = new Random();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ChristmasWeatherManager::tickServer);
        EvolutionBoost.LOGGER.info("[weather] ChristmasWeatherManager initialized.");
    }

    /* =========================================================
       Public API – Commands
       ========================================================= */

    /** /eb weather christmas storm on */
    public static void startChristmasStorm(MinecraftServer server) {
        if (server == null) return;

        // Weather muss aktiviert sein
        if (!EventConfig.isChristmasWeatherEnabled()) {
            EvolutionBoost.LOGGER.warn("[weather] Cannot start storm - christmas weather is disabled in config!");
            return;
        }

        ServerLevel level = getChristmasLevel(server);
        if (level == null) {
            EvolutionBoost.LOGGER.warn("[weather] Cannot start storm - event:christmas dimension not loaded!");
            return;
        }

        christmasState = StormState.PREPARE;
        christmasTicks = 0;

        broadcastToChristmas(server,
                Component.literal("❄ ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("A fierce blizzard is brewing... Seek shelter!").withStyle(ChatFormatting.AQUA))
        );

        EvolutionBoost.LOGGER.info("[weather] Christmas storm cycle STARTED (PREPARE).");
    }

    /** /eb weather christmas storm off */
    public static void stopChristmasStorm(MinecraftServer server) {
        if (server == null) return;

        applyBaseBoosts(server);
        clearChristmasWeather(server);
        removeBossbar();

        christmasState = StormState.IDLE;
        christmasTicks = 0;
        ticksSinceLastStorm = 0;

        broadcastToChristmas(server,
                Component.literal("☀ ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("The blizzard has been cleared.").withStyle(ChatFormatting.GRAY))
        );

        EvolutionBoost.LOGGER.info("[weather] Christmas storm STOPPED (manual).");
    }

    /** /eb weather christmas auto on */
    public static void enableAutoStorm(MinecraftServer server) {
        // Weather muss aktiviert sein
        if (!EventConfig.isChristmasWeatherEnabled()) {
            EvolutionBoost.LOGGER.warn("[weather] Cannot enable auto storm - christmas weather is disabled!");
            return;
        }
        
        EventConfig.setChristmasAutoStorm(true);
        
        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        int autoIntervalTicks = cfg.stormEveryMinutes * 20 * 60;
        
        // Zufällige Startzeit (zwischen 0 und 30 Minuten warten bis zum ersten Sturm)
        ticksSinceLastStorm = RANDOM.nextInt(autoIntervalTicks / 2);

        ensureBaseBoosts(server);

        int minutesUntilFirst = (autoIntervalTicks - ticksSinceLastStorm) / (20 * 60);

        broadcastToChristmas(server,
                Component.literal("⚙ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Automatic blizzard cycle ENABLED. First storm in ~" + minutesUntilFirst + " minutes.")
                                .withStyle(ChatFormatting.AQUA))
        );

        EvolutionBoost.LOGGER.info("[weather] Auto storm cycle ENABLED. First storm in ~{} minutes.", minutesUntilFirst);
    }

    /** /eb weather christmas auto off */
    public static void disableAutoStorm(MinecraftServer server) {
        EventConfig.setChristmasAutoStorm(false);

        broadcastToChristmas(server,
                Component.literal("⚙ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Automatic blizzard cycle DISABLED.").withStyle(ChatFormatting.GRAY))
        );

        EvolutionBoost.LOGGER.info("[weather] Auto storm cycle DISABLED.");
    }

    /** /eb weather christmas init */
    public static void initializeChristmasBoosts(MinecraftServer server) {
        if (!EventConfig.isChristmasWeatherEnabled()) {
            EvolutionBoost.LOGGER.warn("[weather] Cannot initialize boosts - christmas weather is disabled!");
            return;
        }
        
        applyBaseBoosts(server);

        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;

        broadcastToChristmas(server,
                Component.literal("✨ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("Christmas boosts initialized: ALL x" + cfg.baseMultiplier)
                                .withStyle(ChatFormatting.GREEN))
        );
    }

    /** /eb weather christmas status */
    public static String getStatus() {
        if (!EventConfig.isChristmasWeatherEnabled()) {
            return "Christmas Weather: DISABLED (no performance impact)";
        }
        
        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        int stormDurationTicks = cfg.stormDurationMinutes * 20 * 60;
        int autoIntervalTicks = cfg.stormEveryMinutes * 20 * 60;

        StringBuilder sb = new StringBuilder();
        sb.append("State: ").append(christmasState.name());

        if (christmasState == StormState.ACTIVE) {
            int remaining = stormDurationTicks - christmasTicks;
            sb.append(" (").append(formatTicks(remaining)).append(" remaining)");
            sb.append(" | Boosts: ALL x").append(cfg.stormMultiplier);
        } else if (christmasState == StormState.PREPARE) {
            int remaining = PREPARE_TICKS - christmasTicks;
            sb.append(" (storm starts in ").append(formatTicks(remaining)).append(")");
        } else {
            sb.append(" | Boosts: ALL x").append(cfg.baseMultiplier);
            if (cfg.autoStormEnabled) {
                int remaining = autoIntervalTicks - ticksSinceLastStorm;
                sb.append(" | Next storm in ~").append(formatTicks(remaining));
            }
        }

        sb.append(" | Auto: ").append(cfg.autoStormEnabled ? "ON" : "OFF");

        return sb.toString();
    }

    public static boolean isStormActive() {
        return christmasState == StormState.ACTIVE;
    }

    /* =========================================================
       Tick-Logik
       ========================================================= */

    private static void tickServer(MinecraftServer server) {
        if (server == null) return;

        // PERFORMANCE: Komplett überspringen wenn Weather deaktiviert
        if (!EventConfig.isChristmasWeatherEnabled()) {
            return;
        }

        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        int autoIntervalTicks = cfg.stormEveryMinutes * 20 * 60;

        // Auto-Zyklus (mit etwas Zufälligkeit)
        if (cfg.autoStormEnabled && christmasState == StormState.IDLE) {
            ticksSinceLastStorm++;

            if (ticksSinceLastStorm >= autoIntervalTicks) {
                startChristmasStorm(server);
                ticksSinceLastStorm = 0;
            }
        }

        if (christmasState == StormState.IDLE) return;

        christmasTicks++;

        switch (christmasState) {
            case PREPARE -> tickChristmasPrepare(server);
            case ACTIVE -> tickChristmasActive(server);
        }
    }

    private static void tickChristmasPrepare(MinecraftServer server) {
        int remaining = PREPARE_TICKS - christmasTicks;

        // Countdown-Nachrichten
        if (remaining == 20 * 20) { // 20 Sekunden
            broadcastToChristmas(server,
                    Component.literal("❄ ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("20 seconds until the blizzard hits!").withStyle(ChatFormatting.YELLOW))
            );
        } else if (remaining == 20 * 10) { // 10 Sekunden
            broadcastToChristmas(server,
                    Component.literal("❄ ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("10 seconds! Find shelter NOW!").withStyle(ChatFormatting.RED))
            );
        } else if (remaining == 20 * 5) { // 5 Sekunden
            broadcastToChristmas(server,
                    Component.literal("❄❄❄ ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("5...").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
            );
        }

        if (christmasTicks >= PREPARE_TICKS) {
            // Wechsel zu ACTIVE
            christmasState = StormState.ACTIVE;
            christmasTicks = 0;

            setChristmasWeather(server, true);
            applyStormBoosts(server);
            createBossbar(server);

            EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;

            broadcastToChristmas(server,
                    Component.literal("❄❄❄ ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("A HOWLING BLIZZARD ENGULFS THE REALM!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            );
            broadcastToChristmas(server,
                    Component.literal("   ✨ ALL BOOSTS now x").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(String.valueOf(cfg.stormMultiplier)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal(" (Shiny, XP, IV) for " + cfg.stormDurationMinutes + " minutes!").withStyle(ChatFormatting.GRAY))
            );

            EvolutionBoost.LOGGER.info("[weather] Christmas storm ACTIVE. Duration: {} minutes.", cfg.stormDurationMinutes);
        }
    }

    private static void tickChristmasActive(MinecraftServer server) {
        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        int stormDurationTicks = cfg.stormDurationMinutes * 20 * 60;
        int calmWarningTicks = stormDurationTicks - (20 * 30); // 30 Sekunden vor Ende
        
        // Effekte anwenden (alle 2 Sekunden)
        if (christmasTicks % EFFECT_INTERVAL == 0) {
            applyChristmasStormEffects(server);
        }

        // Bossbar aktualisieren (nur jede Sekunde = 20 Ticks für bessere Performance)
        if (christmasTicks % 20 == 0) {
            updateBossbar(server);
        }

        // 30 Sekunden vor Ende: Ankündigung
        if (christmasTicks == calmWarningTicks) {
            broadcastToChristmas(server,
                    Component.literal("❄ ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("The blizzard is beginning to calm down... 30 seconds remaining.").withStyle(ChatFormatting.AQUA))
            );
            EvolutionBoost.LOGGER.info("[weather] Christmas storm CALMING phase (30s remaining).");
        }

        // Ende nach X Minuten
        if (christmasTicks >= stormDurationTicks) {
            applyBaseBoosts(server);
            clearChristmasWeather(server);
            removeBossbar();

            christmasState = StormState.IDLE;
            christmasTicks = 0;

            broadcastToChristmas(server,
                    Component.literal("☀ ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("The blizzard has passed. Peace returns to the realm.").withStyle(ChatFormatting.GRAY))
            );
            broadcastToChristmas(server,
                    Component.literal("   ✨ Boosts back to x").withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(String.valueOf(cfg.baseMultiplier)).withStyle(ChatFormatting.GREEN))
            );

            EvolutionBoost.LOGGER.info("[weather] Christmas storm ended. Base boosts restored.");
        }
    }

    /* =========================================================
       Bossbar Management
       ========================================================= */

    private static void createBossbar(MinecraftServer server) {
        if (stormBossbar != null) {
            removeBossbar();
        }

        stormBossbar = new ServerBossEvent(
                Component.literal("❄ BLIZZARD ❄").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS
        );

        // Alle Spieler in der Christmas-Dimension hinzufügen
        ServerLevel level = getChristmasLevel(server);
        if (level != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel() == level) {
                    stormBossbar.addPlayer(player);
                }
            }
        }
    }

    private static void updateBossbar(MinecraftServer server) {
        if (stormBossbar == null) return;

        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        int stormDurationTicks = cfg.stormDurationMinutes * 20 * 60;

        // Progress berechnen (1.0 = voll, 0.0 = leer)
        float progress = 1.0f - ((float) christmasTicks / (float) stormDurationTicks);
        stormBossbar.setProgress(Math.max(0f, Math.min(1f, progress)));

        // Verbleibende Zeit im Titel
        int remainingSeconds = (stormDurationTicks - christmasTicks) / 20;
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;

        stormBossbar.setName(
                Component.literal("❄ BLIZZARD ❄ ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                        .append(Component.literal(String.format("%d:%02d", minutes, seconds)).withStyle(ChatFormatting.WHITE))
        );

        // Spieler aktualisieren (neue Spieler hinzufügen, alte entfernen)
        ServerLevel level = getChristmasLevel(server);
        if (level != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel() == level) {
                    if (!stormBossbar.getPlayers().contains(player)) {
                        stormBossbar.addPlayer(player);
                    }
                } else {
                    if (stormBossbar.getPlayers().contains(player)) {
                        stormBossbar.removePlayer(player);
                    }
                }
            }
        }
    }

    private static void removeBossbar() {
        if (stormBossbar != null) {
            for (ServerPlayer player : new ArrayList<>(stormBossbar.getPlayers())) {
                stormBossbar.removePlayer(player);
            }
            stormBossbar = null;
        }
    }

    /* =========================================================
       Boost-Management
       ========================================================= */

    private static void applyBaseBoosts(MinecraftServer server) {
        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        double baseMult = cfg.baseMultiplier;

        BoostManager bm = BoostManager.get(server);

        for (BoostType type : CHRISTMAS_BOOST_TYPES) {
            bm.setDimensionMultiplier(type, CHRISTMAS_DIM, baseMult);
        }

        christmasBoostsInitialized = true;
        EvolutionBoost.LOGGER.info("[weather] Christmas BASE boosts applied: ALL x{}", baseMult);
    }

    private static void applyStormBoosts(MinecraftServer server) {
        EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
        double stormMult = cfg.stormMultiplier;

        BoostManager bm = BoostManager.get(server);

        for (BoostType type : CHRISTMAS_BOOST_TYPES) {
            bm.setDimensionMultiplier(type, CHRISTMAS_DIM, stormMult);
        }

        EvolutionBoost.LOGGER.info("[weather] Christmas STORM boosts applied: ALL x{}", stormMult);
    }

    private static void ensureBaseBoosts(MinecraftServer server) {
        if (!christmasBoostsInitialized) {
            applyBaseBoosts(server);
        }
    }

    /* =========================================================
       Wetter setzen / löschen
       ========================================================= */

    private static ServerLevel getChristmasLevel(MinecraftServer server) {
        if (server == null) return null;
        return server.getLevel(CHRISTMAS_DIM);
    }

    private static void setChristmasWeather(MinecraftServer server, boolean storm) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) {
            EvolutionBoost.LOGGER.warn("[weather] Christmas dimension not found.");
            return;
        }

        if (storm) {
            EventConfig.ChristmasSettings cfg = EventConfig.get().christmas;
            int stormDurationTicks = cfg.stormDurationMinutes * 20 * 60;
            // Sturm setzen: rain + thunder für die Dauer + etwas Puffer
            int duration = stormDurationTicks + (20 * 60 * 5); // +5 Minuten Puffer
            level.setWeatherParameters(0, duration, true, true);
            EvolutionBoost.LOGGER.info("[weather] Weather set to STORM (rain+thunder).");
        } else {
            // Klar setzen
            level.setWeatherParameters(20 * 60 * 60, 0, false, false);
            EvolutionBoost.LOGGER.info("[weather] Weather set to CLEAR.");
        }
    }

    private static void clearChristmasWeather(MinecraftServer server) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) return;

        // Wetter auf klar
        level.setWeatherParameters(20 * 60 * 60, 0, false, false);

        // Spieler auftauen und Effekte entfernen
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() == level) {
                try {
                    player.setTicksFrozen(0);
                    player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                } catch (Throwable ignored) {}
            }
        }

        EvolutionBoost.LOGGER.info("[weather] Christmas weather cleared, players defrosted.");
    }

    /* =========================================================
       Storm-Effekte auf Spieler
       ========================================================= */

    private static void applyChristmasStormEffects(MinecraftServer server) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() != level) continue;

            BlockPos pos = player.blockPosition();
            boolean openSky = level.canSeeSky(pos);
            boolean nearHeat = isNearHeatSource(level, pos, 6);
            boolean underRoof = isUnderRoof(level, pos);

            try {
                int current = player.getTicksFrozen();
                int required = player.getTicksRequiredToFreeze();

                if (openSky && !nearHeat) {
                    // Unter freiem Himmel & keine Wärme -> SOFORT voll einfrieren
                    player.setTicksFrozen(required);

                    // Slowness II
                    player.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            20 * 5, 1, false, true, true
                    ));

                    // Schaden wenn voll eingefroren (alle DAMAGE_INTERVAL Ticks)
                    if (christmasTicks % DAMAGE_INTERVAL == 0) {
                        DamageSource freezeDamage = level.damageSources().freeze();
                        player.hurt(freezeDamage, 1.0f); // 0.5 Herzen
                    }

                    // Warnung
                    player.displayClientMessage(
                            Component.literal("❄ You're freezing! Find shelter or warmth!")
                                    .withStyle(ChatFormatting.AQUA),
                            true
                    );

                } else if (!underRoof && !nearHeat) {
                    // Unter Überhang (nicht offener Himmel, aber kein echtes Dach) -> langsam einfrieren
                    int next = Math.min(required, current + 2);
                    player.setTicksFrozen(next);

                    // Leichte Slowness
                    player.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN,
                            20 * 3, 0, false, true, true
                    ));

                } else if (nearHeat) {
                    // Bei Wärmequelle -> schnell auftauen
                    player.setTicksFrozen(Math.max(0, current - 15));

                } else {
                    // Unter echtem Dach -> langsam auftauen
                    player.setTicksFrozen(Math.max(0, current - 5));
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Prüft ob Spieler unter einem echten Dach ist (mindestens 2 solide Blöcke über ihm)
     */
    private static boolean isUnderRoof(ServerLevel level, BlockPos pos) {
        int solidCount = 0;
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();

        for (int y = 1; y <= 10; y++) {
            check.set(pos.getX(), pos.getY() + y, pos.getZ());
            if (!level.getBlockState(check).isAir()) {
                solidCount++;
                if (solidCount >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Prüft ob eine Wärmequelle in der Nähe ist (inkl. Torches, Lanterns)
     * 
     * Performance-Hinweis: Radius 4 prüft 729 Blöcke (9³), was akzeptabel ist.
     * Bei Radius 6 wären es 2197 Blöcke (13³) - zu viel!
     */
    private static boolean isNearHeatSource(ServerLevel level, BlockPos center, int radius) {
        // Limitiere Radius für Performance
        int safeRadius = Math.min(radius, 4);
        
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -safeRadius; dx <= safeRadius; dx++) {
            for (int dy = -safeRadius; dy <= safeRadius; dy++) {
                for (int dz = -safeRadius; dz <= safeRadius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    var state = level.getBlockState(cursor);

                    if (state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                            || state.is(Blocks.FIRE) || state.is(Blocks.LAVA)
                            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.FURNACE)
                            || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)
                            || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                            || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)
                            || state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)
                            || state.is(Blocks.JACK_O_LANTERN)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /* =========================================================
       Helper
       ========================================================= */

    private static void broadcastToChristmas(MinecraftServer server, Component msg) {
        ServerLevel level = getChristmasLevel(server);
        if (level == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel() == level) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}