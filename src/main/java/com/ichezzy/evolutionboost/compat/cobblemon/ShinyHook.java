package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.world.HalloweenWeatherHook;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ThreadLocalRandom;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

@SuppressWarnings({"unchecked"})
public final class ShinyHook {
    private ShinyHook() {}

    /** TEST: Fürs Debuggen hart x100 – danach bitte wieder auf false setzen. */
    private static final boolean TEST_FORCE_MULT = false;
    private static final double  TEST_MULT_VALUE = 100.0;

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        // Event: SHINY_CHANCE_CALCULATION
        subscribeField(clsEvents, priority, "SHINY_CHANCE_CALCULATION", ev -> {
            tryShinyEvent(server, ev);
            return unit();
        });

        // Fallback: POKEMON_ENTITY_SPAWN
        subscribeField(clsEvents, priority, "POKEMON_ENTITY_SPAWN", ev -> {
            tryShinySpawnFallback(server, ev);
            return unit();
        });
    }

    /** SHINY_CHANCE_CALCULATION: Chance = current * multiplier. */
    private static void tryShinyEvent(MinecraftServer server, Object ev) {
        try {
            double baseMult = TEST_FORCE_MULT ? TEST_MULT_VALUE
                    : BoostManager.get(server).getMultiplierFor(BoostType.SHINY, null);

            // addModificationFunction(Function3<Float, ServerPlayer?, Pokemon, Float>)
            Class<?> fn3 = Class.forName("kotlin.jvm.functions.Function3");
            Method addFn = find(ev.getClass(), "addModificationFunction", fn3);
            if (addFn != null) {
                final double capturedBase = baseMult; // effectively final
                Object lambda = Proxy.newProxyInstance(fn3.getClassLoader(), new Class<?>[]{ fn3 },
                        (proxy, m, args) -> {
                            String name = m.getName();
                            if ("invoke".equals(name)) {
                                float current = ((Number) args[0]).floatValue();
                                ServerPlayer player = null;
                                try { player = (ServerPlayer) args[1]; } catch (Throwable ignored) {}

                                double effective = capturedBase;
                                // x2 nur, wenn Halloween-Sturm aktiv + Spieler in der Halloween-Dimension
                                try {
                                    if (player != null && HalloweenWeatherHook.shinyBoostActiveFor(player)) {
                                        effective *= 2.0;
                                    }
                                } catch (Throwable ignored) { /* Hook optional */ }

                                float out = (float) Math.max(0.0, current * (float) effective);
                                return out; // autobox to Float OK
                            }
                            // Object method handling for proxy robustness
                            if ("equals".equals(name)) return proxy == args[0];
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("toString".equals(name)) return "Function3Proxy(ShinyChance)";
                            return null;
                        });
                addFn.invoke(ev, lambda);
                return;
            }

            // Fallback: addModifier(delta)
            Method addMod = find(ev.getClass(), "addModifier", float.class);
            Float baseChance = tryGetFloat(ev, "getBaseChance", "baseChance");
            if (addMod != null && baseChance != null) {
                float target = (float) Math.max(0.0, baseChance * (float) baseMult);
                float delta  = target - baseChance;
                addMod.invoke(ev, delta);
            }
        } catch (Throwable ignored) {}
    }

    /** Spawn-Fallback: zusätzliche unabhängige Rolls, nur wenn noch nicht shiny. */
    private static void tryShinySpawnFallback(MinecraftServer server, Object ev) {
        try {
            double baseMult = TEST_FORCE_MULT ? TEST_MULT_VALUE
                    : BoostManager.get(server).getMultiplierFor(BoostType.SHINY, null);

            Method mGetEntity = find(ev.getClass(), "getEntity");
            if (mGetEntity == null) return;
            Object entity = mGetEntity.invoke(ev);
            if (entity == null) return;

            Class<?> clsPokemonEntity = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity");
            if (!clsPokemonEntity.isInstance(entity)) return;

            final double capturedBase = baseMult; // effectively final
            server.execute(() -> {
                try {
                    Object pokemon = clsPokemonEntity.getMethod("getPokemon").invoke(entity);
                    if (pokemon == null) return;

                    Method getIsShiny = findAny(pokemon.getClass(), "isShiny", "getShiny");
                    boolean already = getIsShiny != null && Boolean.TRUE.equals(getIsShiny.invoke(pokemon));
                    if (already) return;

                    // prüfe Dimension auf x2
                    double effective = capturedBase;
                    try {
                        Object levelObj = clsPokemonEntity.getMethod("level").invoke(entity);
                        if (levelObj instanceof ServerLevel sl && HalloweenWeatherHook.shinyBoostActiveIn(sl)) {
                            effective *= 2.0;
                        }
                    } catch (Throwable ignored) { /* optional */ }

                    final double p0 = 1.0 / 4096.0;
                    final int rolls  = Math.max(1, (int) Math.ceil(effective));
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    for (int i = 0; i < rolls; i++) {
                        if (rnd.nextDouble() < p0) {
                            Method setShiny = find(pokemon.getClass(), "setShiny", boolean.class);
                            if (setShiny != null) setShiny.invoke(pokemon, Boolean.TRUE);
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }
}
