package com.ichezzy.evolutionboost.compat.cobblemon;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DROP-Hook:
 * - hängt an CobblemonEvents.LOOT_DROPPED
 * - skaliert die Drop-Liste anhand des DROP-Boosts (GLOBAL × DIMENSION)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class DropHook {

    private DropHook() {}

    /**
     * Alte Signatur für HooksRegistrar – clsEvents/priority werden nicht mehr benötigt.
     */
    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        register(server);
    }

    /** Eigentliche Registrierung am Cobblemon-Event. */
    private static void register(MinecraftServer server) {
        CobblemonEvents.LOOT_DROPPED.subscribe(
                Priority.NORMAL,
                new Function1<Object, Unit>() {
                    @Override
                    public Unit invoke(Object ev) {
                        try {
                            handleLoot(server, ev);
                        } catch (Throwable t) {
                            EvolutionBoost.LOGGER.warn(
                                    "[compat][drop] error in loot handler: {}",
                                    t.toString()
                            );
                        }
                        return Unit.INSTANCE;
                    }
                }
        );

        EvolutionBoost.LOGGER.info("[compat][drop] LOOT_DROPPED hook registered.");
    }

    /**
     * Wird bei jedem LootDroppedEvent aufgerufen.
     *
     * Erwartet (Kotlin-Seite):
     *   - val player: ServerPlayer?
     *   - val entity: LivingEntity?
     *   - val drops: MutableList<DropEntry>
     */
    private static void handleLoot(MinecraftServer server, Object ev) {
        // --- 1) Spieler / Entity & Dimension ermitteln ---
        ServerPlayer player = extractPlayer(ev, server);
        LivingEntity entity = extractEntity(ev);

        ResourceKey<Level> dimKey = null;

        if (player != null) {
            ServerLevel lvl = player.serverLevel();
            dimKey = lvl.dimension();
        } else if (entity != null && entity.level() instanceof ServerLevel sl) {
            dimKey = sl.dimension();
        }

        // --- 2) Multiplier bestimmen (GLOBAL × DIMENSION) ---
        BoostManager bm = BoostManager.get(server);
        double mult;

        if (dimKey != null) {
            // Dimension-spezifischer Multiplier (inkl. global)
            mult = bm.getMultiplierFor(BoostType.DROP, null, dimKey);
        } else {
            // Fallback: nur globaler Multiplier
            mult = bm.getMultiplierFor(BoostType.DROP, null);
        }

        if (mult <= 1.0) {
            return; // kein Drop-Boost aktiv
        }

        // Optionale Sicherheitsbremse gegen völlig absurde Multiplier
        if (mult > 10.0) {
            mult = 10.0;
        }

        // --- 3) Drop-Liste holen ---
        List<Object> drops = getDropList(ev);
        if (drops == null || drops.isEmpty()) {
            return;
        }

        // --- 4) Duplikate berechnen ---
        double extraTotal = mult - 1.0; // z.B. 1.0 bei x2, 2.0 bei x3 usw.
        int baseExtra = (int) Math.floor(extraTotal);
        double fracExtra = extraTotal - baseExtra;

        // Random für den fractional Anteil
        java.util.Random rand = new java.util.Random();

        List<Object> additions = new ArrayList<>();

        for (Object entry : drops) {
            if (entry == null) continue;

            // Füge baseExtra mal dieselbe Entry hinzu (z.B. bei x2 genau einmal)
            for (int i = 0; i < baseExtra; i++) {
                additions.add(entry);
            }

            // Fractional Anteil: z.B. mult=2.5 => 1 garantierte + 50% Chance auf eine weitere Kopie
            if (fracExtra > 0.0 && rand.nextDouble() < fracExtra) {
                additions.add(entry);
            }
        }

        if (!additions.isEmpty()) {
            try {
                drops.addAll(additions);
            } catch (Throwable t) {
                EvolutionBoost.LOGGER.warn(
                        "[compat][drop] failed to extend drops list: {}",
                        t.toString()
                );
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Reflection-Helper                                                  */
    /* ------------------------------------------------------------------ */

    private static ServerPlayer extractPlayer(Object ev, MinecraftServer server) {
        // Erstes Ziel: event.getPlayer()
        try {
            Method m = ev.getClass().getMethod("getPlayer");
            Object o = m.invoke(ev);
            if (o instanceof ServerPlayer sp) {
                return sp;
            }
        } catch (Throwable ignored) {}

        // Fallback: event.getPlayerId(): UUID -> daraus den Player holen
        try {
            Method m = ev.getClass().getMethod("getPlayerId");
            Object o = m.invoke(ev);
            if (o instanceof UUID uuid) {
                return server.getPlayerList().getPlayer(uuid);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static LivingEntity extractEntity(Object ev) {
        try {
            Method m = ev.getClass().getMethod("getEntity");
            Object o = m.invoke(ev);
            if (o instanceof LivingEntity le) {
                return le;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getDropList(Object ev) {
        try {
            Method m = ev.getClass().getMethod("getDrops");
            Object o = m.invoke(ev);
            if (o instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
