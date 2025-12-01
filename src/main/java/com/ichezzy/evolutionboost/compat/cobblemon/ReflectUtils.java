package com.ichezzy.evolutionboost.compat.cobblemon;

import java.lang.reflect.*;
import java.util.function.Function;

public final class ReflectUtils {
    private ReflectUtils() {}

    /* ------------------ Method-Finder ------------------ */

    /** Exakte Methode mit Parametern. */
    public static Method find(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Exception ignored) {}
        try {
            Method m = c.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Exception ignored) {}
        return null;
    }

    /** Erste Methode mit einem der Namen, ohne Parametertypen zu erzwingen. */
    public static Method findAny(Class<?> c, String... names) {
        for (String n : names) {
            // public Methoden
            for (Method m : c.getMethods()) {
                if (m.getName().equals(n)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            // declared Methoden
            try {
                Method m = c.getDeclaredMethod(n);
                m.setAccessible(true);
                return m;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /* ------------------ Event-Subscription ------------------ */

    /**
     * Sucht in CobblemonEvents ein Feld mit Namen {@code fieldName}, holt dessen Observable-Instanz
     * und ruft eine passende {@code subscribe(...)}-Methode auf.
     *
     * Unterstützte Signaturen:
     *  - subscribe(Consumer<T>)
     *  - subscribe(Priority, Consumer<T>)
     *  - subscribe(Function1<T, Unit>)   (Kotlin)
     *  - subscribe(Priority, Function1<T, Unit>)
     *
     * Unser {@code listener} ist ein {@code Function<Object,Object>}, das vom Adapter aufgerufen wird.
     */
    public static void subscribeField(Class<?> eventsClass,
                                      Object priorityEnumOrNull,
                                      String fieldName,
                                      Function<Object, Object> listener) {
        try {
            Field f = eventsClass.getField(fieldName);
            f.setAccessible(true);
            Object observable = f.get(null);
            if (observable == null) return;

            Method[] methods = observable.getClass().getMethods();
            for (Method m : methods) {
                if (!m.getName().equals("subscribe")) continue;
                Class<?>[] params = m.getParameterTypes();

                try {
                    if (params.length == 1) {
                        Object handler = adaptSubscriber(params[0], listener);
                        if (handler != null) {
                            m.setAccessible(true);
                            m.invoke(observable, handler);
                            return;
                        }
                    } else if (params.length == 2 && priorityEnumOrNull != null) {
                        // z.B. subscribe(Priority, Consumer) oder subscribe(Priority, Function1)
                        if (!params[0].isInstance(priorityEnumOrNull)) continue;
                        Object handler = adaptSubscriber(params[1], listener);
                        if (handler != null) {
                            m.setAccessible(true);
                            m.invoke(observable, priorityEnumOrNull, handler);
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                    // nächste subscribe-Variante testen
                }
            }
        } catch (Throwable ignored) {
            // Event-Feld existiert nicht / API anders -> Hook fällt still aus
        }
    }

    /**
     * Probiert mehrere Feldnamen (für verschiedene Cobblemon-Versionen) und nimmt das erste,
     * das funktioniert.
     */
    public static void subscribeFieldOptional(Class<?> eventsClass,
                                              Object priorityEnumOrNull,
                                              String[] possibleFields,
                                              Function<Object, Object> listener) {
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

    /**
     * Baut aus unserem {@code Function<Object,Object>} ein Objekt, das in die
     * Cobblemon-Observable.subscribe-Signatur passt:
     *
     *  - java.util.function.Consumer  → Consumer-Adapter
     *  - kotlin.jvm.functions.Function1 → Proxy, der invoke(e) → listener.apply(e) ruft
     */
    private static Object adaptSubscriber(Class<?> paramType, Function<Object, Object> listener) {
        // 1) Java-Consumer
        if (java.util.function.Consumer.class.isAssignableFrom(paramType)) {
            return (java.util.function.Consumer<Object>) listener::apply;
        }

        // 2) Kotlin Function1<T, R>
        if ("kotlin.jvm.functions.Function1".equals(paramType.getName())) {
            return Proxy.newProxyInstance(
                    paramType.getClassLoader(),
                    new Class<?>[]{paramType},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("invoke".equals(name) && args != null && args.length == 1) {
                            // Event reinschieben, Rückgabewert interessiert Cobblemon nicht → null
                            return listener.apply(args[0]);
                        }
                        // ein paar Default-Implementierungen für Object-Methoden
                        if ("toString".equals(name)) return "EvolutionBoostFunction1Adapter";
                        if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                        if ("equals".equals(name)) return proxy == args[0];
                        return null;
                    }
            );
        }

        // Fallback: falls das Ding einfach Object akzeptiert oder unsere Function direkt
        if (paramType.isInstance(listener)) {
            return listener;
        }

        return null;
    }

    /** Mini-Helper für Listener: Rückgabewert als "Unit". */
    public static Object unit() { return null; }
}
