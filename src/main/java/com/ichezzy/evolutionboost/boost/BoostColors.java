package com.ichezzy.evolutionboost.boost;

import net.minecraft.ChatFormatting;
import net.minecraft.world.BossEvent;

public final class BoostColors {
    private BoostColors() {}

    /** Bossbar-Farbe pro BoostType. */
    public static BossEvent.BossBarColor color(BoostType type) {
        return switch (type) {
            case SHINY -> BossEvent.BossBarColor.YELLOW;
            case XP    -> BossEvent.BossBarColor.GREEN;
            case DROP  -> BossEvent.BossBarColor.BLUE;
            case IV    -> BossEvent.BossBarColor.PURPLE;
        };
    }

    /** Chat-/Textfarbe pro BoostType. */
    public static ChatFormatting chatColor(BoostType type) {
        return switch (type) {
            case SHINY -> ChatFormatting.GOLD;
            case XP    -> ChatFormatting.GREEN;
            case DROP  -> ChatFormatting.AQUA;
            case IV    -> ChatFormatting.DARK_PURPLE;
        };
    }
}
