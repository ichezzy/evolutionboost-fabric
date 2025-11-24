package com.ichezzy.evolutionboost.compat.cobblemon;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReflectUtils {
    private ReflectUtils() {}

    // ... deine bestehenden Methoden bleiben; ergänzt wird Folgendes:

    /** Bestehende Utility aus deinem Projekt (angenommen): */
    public static Method find(Class<?> c, String name, Class<?>... params) {
        try { return c.getDeclaredMethod(name, params); } catch (Exception ignored) { return null; }
    }
    public static Method findAny(Class<?> c, String... names) {
        for (String n : names) {
            for (Method m : c.getMethods()) if (m.getName().equals(n)) return m;
            try { Method m = c.getDeclaredMethod(n); m.setAccessible(true); return m; } catch (Exception ignored) {}
        }
        return null;
    }

    /** Dein bestehendes subscribeField(...) wird hier vorausgesetzt. */
    public static void subscribeField(Class<?> eventsClass, Object priorityEnumOrNull, String fieldName, Function<Object,Object> listener) {
        try {
            Field f = eventsClass.getField(fieldName);
            Object event = f.get(null);
            // erwartete Signatur: event.subscribe(priority?, listener)
            Method subscribe = findAny(event.getClass(), "subscribe");
            if (subscribe == null) return;
            if (subscribe.getParameterCount() == 2 && priorityEnumOrNull != null) {
                subscribe.invoke(event, priorityEnumOrNull, listener);
            } else if (subscribe.getParameterCount() == 1) {
                subscribe.invoke(event, listener);
            }
        } catch (Throwable ignored) {}
    }

    /** Neu: probiert mehrere Feldnamen; nimmt das erste, das existiert. */
    public static void subscribeFieldOptional(Class<?> eventsClass, Object priorityEnumOrNull, String[] possibleFields,
                                              Function<Object,Object> listener) {
        for (String name : possibleFields) {
            try {
                Field f = eventsClass.getField(name);
                if (f != null) {
                    subscribeField(eventsClass, priorityEnumOrNull, name, listener);
                    return;
                }
            } catch (Throwable ignored) {}
        }
    }

    /** Mini-Helper für Listener: Rückgabewert als "Unit". */
    public static Object unit() { return null; }
}
