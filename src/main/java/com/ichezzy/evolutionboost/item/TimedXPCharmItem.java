package com.ichezzy.evolutionboost.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 30-Day XP Charm - grants x1.5 battle XP for the duration.
 */
public class TimedXPCharmItem extends TimedItem {
    
    public static final double XP_MULTIPLIER = 1.5;

    public TimedXPCharmItem(Properties properties, int durationDays) {
        super(properties, durationDays);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("⭐ XP Charm (" + durationDays + " Days)")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        
        appendTimerTooltip(stack, tooltip);
        
        tooltip.add(Component.literal(""));

        if (!isExpired(stack)) {
            tooltip.add(Component.literal("Increases Battle XP by x" + XP_MULTIPLIER)
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("  • Only affects battle XP")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("  • Does NOT affect XP Candies")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("This charm has expired")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !isExpired(stack);
    }

    /**
     * Checks if player has an ACTIVE timed XP charm in inventory.
     */
    public static boolean hasActiveTimedXPCharm(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof TimedXPCharmItem timedCharm) {
                if (timedCharm.isActive(stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
