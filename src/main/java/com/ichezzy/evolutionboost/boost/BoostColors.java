package com.ichezzy.evolutionboost.boost;

import net.minecraft.world.BossEvent;

public final class BoostColors {
    private BoostColors() {}
    public static BossEvent.BossBarColor color(BoostType type) {
        return switch (type) {
            case SHINY -> BossEvent.BossBarColor.YELLOW;
            case XP    -> BossEvent.BossBarColor.GREEN;
            case DROP  -> BossEvent.BossBarColor.BLUE;
            case IV    -> BossEvent.BossBarColor.PURPLE;
        };
    }
}
