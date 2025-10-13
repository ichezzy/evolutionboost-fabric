package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.*;
import java.util.List;

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

            Method mGetDrops = find(ev.getClass(), "getDrops");
            List<?> drops = null;
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

            for (Object entry : drops) {
                scaleDropEntryQuantities(entry, mult);
            }
        } catch (Throwable ignored) {}
    }

    private static void scaleDropEntryQuantities(Object entry, double mult) {
        try {
            Method getRange = find(entry.getClass(), "getQuantityRange");
            Class<?> intRange = Class.forName("kotlin.ranges.IntRange");
            Method setRange = find(entry.getClass(), "setQuantityRange", intRange);
            if (getRange != null && setRange != null) {
                Object range = getRange.invoke(entry);
                if (range != null) {
                    int start = (int) range.getClass().getMethod("getFirst").invoke(range);
                    int end   = (int) range.getClass().getMethod("getLast").invoke(range);
                    int nStart = Math.max(1, (int) Math.round(start * mult));
                    int nEnd   = Math.max(nStart, (int) Math.round(end * mult));
                    Object newRange = intRange.getConstructor(int.class, int.class).newInstance(nStart, nEnd);
                    setRange.invoke(entry, newRange);
                    return;
                }
            }
        } catch (Throwable ignored) {}

        try {
            Method getQty = find(entry.getClass(), "getQuantity");
            Method setQty = find(entry.getClass(), "setQuantity", int.class);
            if (getQty != null && setQty != null) {
                int qty = (Integer) getQty.invoke(entry);
                int nQty = Math.max(1, (int) Math.round(qty * mult));
                setQty.invoke(entry, nQty);
            }
        } catch (Throwable ignored) {}
    }
}
