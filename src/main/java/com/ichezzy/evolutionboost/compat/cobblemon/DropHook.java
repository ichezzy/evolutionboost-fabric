package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

@SuppressWarnings({"rawtypes","unchecked"})
public final class DropHook {
    private DropHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        subscribeField(clsEvents, priority, "LOOT_DROPPED", ev -> {
            tryDrop(server, ev);
            return unit();
        });
    }

    private static void tryDrop(MinecraftServer server, Object ev) {
        try {
            double mult = BoostManager.get(server).getMultiplierFor(BoostType.DROP, null);
            if (mult <= 1.0) return;

            // Liste der bereits ausgewählten DropEntries holen
            List<?> drops = null;
            Method mGetDrops = find(ev.getClass(), "getDrops");
            if (mGetDrops != null) {
                Object obj = mGetDrops.invoke(ev);
                if (obj instanceof List<?> l) drops = l;
            }
            if (drops == null) {
                try {
                    Field fDrops = ev.getClass().getDeclaredField("drops");
                    fDrops.setAccessible(true);
                    Object v = fDrops.get(ev);
                    if (v instanceof List<?> l) drops = l;
                } catch (NoSuchFieldException ignored) {}
            }
            if (drops == null || drops.isEmpty()) return;

            // Dupliziere die Einträge in-place:
            // base = floor(mult), frac = mult - base -> mit Wahrscheinlichkeit "frac" noch einmal hinzufügen
            int base = (int) Math.floor(mult);
            double frac = mult - base;
            if (base <= 1 && frac <= 1e-9) return;

            List snapshot = new ArrayList(drops);  // bereits ausgewählte Einträge
            for (int i = 1; i < base; i++) {
                drops.addAll(snapshot);
            }
            if (frac > 1e-9 && new Random().nextDouble() < frac) {
                drops.addAll(snapshot);
            }
        } catch (Throwable ignored) {}
    }
}
