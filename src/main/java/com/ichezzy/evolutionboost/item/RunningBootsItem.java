package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Running Shoes - Trinket Items die Speed-Boost geben.
 * Funktionieren NUR über Trinkets-Slot (kein ArmorItem mehr).
 * 
 * - Running Shoes: +10% Speed
 * - Great Running Shoes: +20% Speed
 * - Ultra Running Shoes: +30% Speed
 */
public class RunningBootsItem extends Item {

    public enum Tier {
        NORMAL("running_shoes", 0.10, ChatFormatting.WHITE),
        GREAT("great_running_shoes", 0.20, ChatFormatting.BLUE),
        ULTRA("ultra_running_shoes", 0.30, ChatFormatting.GOLD);

        private final String name;
        private final double speedBonus;
        private final ChatFormatting color;

        Tier(String name, double speedBonus, ChatFormatting color) {
            this.name = name;
            this.speedBonus = speedBonus;
            this.color = color;
        }

        public String getName() { return name; }
        public double getSpeedBonus() { return speedBonus; }
        public ChatFormatting getColor() { return color; }
    }

    private final Tier tier;

    public RunningBootsItem(Tier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        
        String displayName = switch (tier) {
            case NORMAL -> "Running Shoes";
            case GREAT -> "Great Running Shoes";
            case ULTRA -> "Ultra Running Shoes";
        };
        
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("⚡ " + displayName).withStyle(tier.getColor(), ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("+" + (int)(tier.getSpeedBonus() * 100) + "% Movement Speed")
                .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Ultra Shoes haben einen glänzenden Effekt
        return tier == Tier.ULTRA;
    }
}
