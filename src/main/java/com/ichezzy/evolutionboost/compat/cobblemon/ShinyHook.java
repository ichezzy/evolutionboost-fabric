package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

@SuppressWarnings({"rawtypes","unchecked"})
public final class ShinyHook {
    private ShinyHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        // Event: SHINY_CHANCE_CALCULATION
        subscribeField(clsEvents, priority, "SHINY_CHANCE_CALCULATION", ev -> {
            tryShinyEvent(server, ev);
            return unit();
        });

        // Fallback beim Spawn: POKEMON_ENTITY_SPAWN
        subscribeField(clsEvents, priority, "POKEMON_ENTITY_SPAWN", ev -> {
            tryShinySpawnFallback(server, ev);
            return unit();
        });
    }

    private static void tryShinyEvent(MinecraftServer server, Object ev) {
        try {
            double mult = BoostManager.get(server).getMultiplierFor(BoostType.SHINY, null);
            if (mult <= 1.0) return;

            Class<?> fn3 = Class.forName("kotlin.jvm.functions.Function3");
            Method addFn = find(ev.getClass(), "addModificationFunction", fn3);
            if (addFn != null) {
                Object lambda = Proxy.newProxyInstance(fn3.getClassLoader(), new Class<?>[]{ fn3 },
                        (proxy, m, args) -> {
                            if (!"invoke".equals(m.getName())) return null;
                            float current = ((Number) args[0]).floatValue();
                            float out = (float) Math.max(0.0, current * (float) mult);
                            return Float.valueOf(out);
                        });
                addFn.invoke(ev, lambda);
                return;
            }

            Method addMod = find(ev.getClass(), "addModifier", float.class);
            Float baseChance = tryGetFloat(ev, "getBaseChance", "baseChance");
            if (addMod != null && baseChance != null) {
                float target = (float) Math.max(0.0, baseChance * (float) mult);
                float delta  = target - baseChance;
                addMod.invoke(ev, delta);
            }
        } catch (Throwable ignored) {}
    }

    private static void tryShinySpawnFallback(MinecraftServer server, Object ev) {
        try {
            double mult = BoostManager.get(server).getMultiplierFor(BoostType.SHINY, null);
            if (mult <= 1.0) return;

            Method mGetEntity = find(ev.getClass(), "getEntity");
            if (mGetEntity == null) return;
            Object entity = mGetEntity.invoke(ev);
            if (entity == null) return;

            Class<?> clsPokemonEntity = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity");
            if (!clsPokemonEntity.isInstance(entity)) return;

            server.execute(() -> {
                try {
                    Object pokemon = clsPokemonEntity.getMethod("getPokemon").invoke(entity);
                    if (pokemon == null) return;

                    Method getIsShiny = findAny(pokemon.getClass(), "isShiny", "getShiny");
                    boolean already = getIsShiny != null && Boolean.TRUE.equals(getIsShiny.invoke(pokemon));
                    if (already) return;

                    double baseP = 1.0 / 4096.0; // Server-config baseline wird i.d.R. in dieses Schema überführt
                    double targetP = Math.min(1.0, baseP * mult);

                    if (ThreadLocalRandom.current().nextDouble() < targetP) {
                        Method setShiny = find(pokemon.getClass(), "setShiny", boolean.class);
                        if (setShiny != null) setShiny.invoke(pokemon, Boolean.TRUE);
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }
}
