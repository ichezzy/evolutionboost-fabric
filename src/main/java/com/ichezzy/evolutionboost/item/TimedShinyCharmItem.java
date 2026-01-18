package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 30-Day Shiny Charm - same function as permanent, but expires.
 */
public class TimedShinyCharmItem extends TimedItem {

    public TimedShinyCharmItem(Properties properties, int durationDays) {
        super(properties, durationDays);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("✨ Shiny Charm (" + durationDays + " Days)")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        
        appendTimerTooltip(stack, tooltip);
        
        tooltip.add(Component.literal(""));

        if (!isExpired(stack) && cfg.shinyCharmEnabled) {
            tooltip.add(Component.literal("Increases Shiny chance for nearby spawns")
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("  • x" + cfg.shinyCharmMultiplier + " multiplier")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("  • " + cfg.shinyCharmRadius + " block radius")
                    .withStyle(ChatFormatting.GRAY));
        } else if (isExpired(stack)) {
            tooltip.add(Component.literal("This charm has expired")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !isExpired(stack);
    }

    /**
     * Checks if player has an ACTIVE timed shiny charm in inventory.
     */
    public static boolean hasActiveTimedShinyCharm(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof TimedShinyCharmItem timedCharm) {
                if (timedCharm.isActive(stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
