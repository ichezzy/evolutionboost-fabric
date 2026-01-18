package com.ichezzy.evolutionboost.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 30-Day Running Shoes - grants speed bonus for the duration.
 */
public class TimedRunningBootsItem extends TimedItem {

    private final RunningBootsItem.Tier tier;

    public TimedRunningBootsItem(RunningBootsItem.Tier tier, Properties properties, int durationDays) {
        super(properties, durationDays);
        this.tier = tier;
    }

    public RunningBootsItem.Tier getTier() {
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
        tooltip.add(Component.literal("⚡ " + displayName + " (" + durationDays + " Days)")
                .withStyle(tier.getColor(), ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        
        appendTimerTooltip(stack, tooltip);
        
        tooltip.add(Component.literal(""));

        if (!isExpired(stack)) {
            tooltip.add(Component.literal("+" + (int)(tier.getSpeedBonus() * 100) + "% Movement Speed")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("These shoes have expired")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == RunningBootsItem.Tier.ULTRA && !isExpired(stack);
    }
}
