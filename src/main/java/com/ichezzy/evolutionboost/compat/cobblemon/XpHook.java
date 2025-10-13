package com.ichezzy.evolutionboost.compat.cobblemon;

import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

import static com.ichezzy.evolutionboost.compat.cobblemon.ReflectUtils.*;

@SuppressWarnings({"rawtypes","unchecked"})
public final class XpHook {
    private XpHook() {}

    public static void register(MinecraftServer server, Class<?> clsEvents, Object priority) {
        subscribeField(clsEvents, priority, "EXPERIENCE_GAINED_EVENT_PRE", ev -> {
            tryXp(server, ev);
            return unit();
        });
    }

    private static void tryXp(MinecraftServer server, Object ev) {
        try {
            Method getSource = find(ev.getClass(), "getSource");
            if (getSource == null) return;
            Object src = getSource.invoke(ev);

            try {
                Class<?> battleSrc = Class.forName("com.cobblemon.mod.common.api.pokemon.experience.BattleExperienceSource");
                if (!battleSrc.isInstance(src)) return;
            } catch (ClassNotFoundException ignore) {
                if (src == null || !src.getClass().getName().contains("Battle")) return;
            }

            Method getExp = find(ev.getClass(), "getExperience");
            Method setExp = find(ev.getClass(), "setExperience", int.class);
            if (getExp == null || setExp == null) return;

            int exp = (Integer) getExp.invoke(ev);
            double mult = BoostManager.get(server).getMultiplierFor(BoostType.XP, null);
            if (mult > 1.0) {
                int boosted = Math.max(1, (int) Math.round(exp * mult));
                setExp.invoke(ev, boosted);
            }
        } catch (Throwable ignored) {}
    }
}
