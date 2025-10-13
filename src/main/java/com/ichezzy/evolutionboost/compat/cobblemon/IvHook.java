package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.*;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

@SuppressWarnings({"rawtypes","unchecked"})
public final class IvHook {
    private IvHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        subscribeField(clsEvents, priority, "POKEMON_ENTITY_SPAWN", ev -> {
            tryIvs(server, ev);
            return unit();
        });
    }

    private static void tryIvs(MinecraftServer server, Object ev) {
        try {
            double mult = BoostManager.get(server).getMultiplierFor(BoostType.IV, null);
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

                    // Stats refs
                    Class<?> statsCls = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");
                    Object HP  = statsCls.getField("HP").get(null);
                    Object ATK = statsCls.getField("ATTACK").get(null);
                    Object DEF = statsCls.getField("DEFENCE").get(null);
                    Object SPA = statsCls.getField("SPECIAL_ATTACK").get(null);
                    Object SPD = statsCls.getField("SPECIAL_DEFENCE").get(null);
                    Object SPE = statsCls.getField("SPEED").get(null);

                    List<Object> stats = new ArrayList<>(Arrays.asList(HP, ATK, DEF, SPA, SPD, SPE));
                    Collections.shuffle(stats, new Random());

                    // NEU: exakt floor(mult) garantierte 31er
                    int guaranteed = Math.min(6, (int) Math.floor(mult));

                    if (guaranteed > 0) {
                        Method mSetIV = findAny(pokemon.getClass(), "setIV", "setIv", "setIvValue");
                        if (mSetIV != null) {
                            for (int i = 0; i < guaranteed; i++) {
                                try { mSetIV.invoke(pokemon, stats.get(i), 31); }
                                catch (IllegalArgumentException wrongOrder) { mSetIV.invoke(pokemon, 31, stats.get(i)); }
                            }
                        } else {
                            EvolutionBoost.LOGGER.debug("[evolutionboost] setIV method not found");
                        }
                    }

                    // evtl. Recalc (sicher ist sicher)
                    Method recalc = findAny(pokemon.getClass(), "recalculateStats", "recalculate", "calculateStats", "refreshStats");
                    if (recalc != null) {
                        try {
                            if (recalc.getParameterCount() == 0) recalc.invoke(pokemon);
                            else if (recalc.getParameterCount() == 1 && recalc.getParameterTypes()[0] == boolean.class)
                                recalc.invoke(pokemon, Boolean.TRUE);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable t) {
                    EvolutionBoost.LOGGER.debug("[evolutionboost] tryIvs (deferred) failed: {}", t.toString());
                }
            });
        } catch (Throwable ignored) {}
    }
}
