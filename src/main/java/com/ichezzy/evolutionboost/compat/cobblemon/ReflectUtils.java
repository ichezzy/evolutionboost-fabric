package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;

import java.lang.reflect.*;
import java.util.*;

@SuppressWarnings({"rawtypes","unchecked"})
public final class ReflectUtils {
    private ReflectUtils() {}

    public interface EH { Object h(Object ev) throws Throwable; }

    public static Object unit() {
        try { return Class.forName("kotlin.Unit").getField("INSTANCE").get(null); }
        catch (Throwable t) { return null; }
    }

    public static void subscribeField(Class<?> clsEvents, Object priority, String fieldName, EH handler) {
        try {
            Field f = null;
            try { f = clsEvents.getField(fieldName); } catch (NoSuchFieldException ignored) {}
            if (f == null) for (Field ff : clsEvents.getFields())
                if (ff.getName().equalsIgnoreCase(fieldName)) { f = ff; break; }
            if (f == null) return;

            Object bus = f.get(null);
            Class<?> fn1 = Class.forName("kotlin.jvm.functions.Function1");
            Method subscribe = find(bus.getClass(), "subscribe", priority.getClass(), fn1);
            if (subscribe != null) {
                Object lambda = Proxy.newProxyInstance(fn1.getClassLoader(), new Class<?>[]{fn1},
                        (proxy, m, args) -> "invoke".equals(m.getName())
                                ? safe(handler, args != null && args.length > 0 ? args[0] : null)
                                : null);
                subscribe.invoke(bus, priority, lambda);
                EvolutionBoost.LOGGER.info("[evolutionboost] subscribed: {}", f.getName());
            }
        } catch (Throwable t) {
            EvolutionBoost.LOGGER.debug("[evolutionboost] subscribeField {} failed: {}", fieldName, t.toString());
        }
    }

    public static Object safe(EH h, Object ev) {
        try { return h.h(ev); } catch (Throwable t) {
            EvolutionBoost.LOGGER.debug("[evolutionboost] handler error: {}", t.toString());
            return unit();
        }
    }

    public static Method find(Class<?> c, String name, Class<?>... params) {
        try { return c.getMethod(name, params); } catch (NoSuchMethodException e) { return null; }
    }

    public static Method findAny(Class<?> c, String... names) {
        for (String n : names) for (Method m : c.getMethods()) if (m.getName().equals(n)) return m;
        return null;
    }

    public static Float tryGetFloat(Object ev, String getter, String fieldName) {
        try {
            Method g = find(ev.getClass(), getter);
            if (g != null) return (Float) g.invoke(ev);
            Field f = ev.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return (Float) f.get(ev);
        } catch (Throwable t) { return null; }
    }

    public static void amplifyListInPlace(List list, double mult) {
        int base = (int) Math.floor(mult);
        double frac = mult - base;
        if (base <= 1 && frac <= 1e-9) return;
        List snapshot = new ArrayList(list);
        for (int i = 1; i < base; i++) list.addAll(snapshot);
        if (frac > 1e-9 && new Random().nextDouble() < frac) list.addAll(snapshot);
    }
}
