package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * XP Charm - grants bonus battle XP when in inventory or trinket slot.
 * Only affects battle XP, NOT XP Candies!
 * 
 * Configurable in evolutionboost.json:
 * - xpCharmEnabled: true/false
 * - xpCharmMultiplier: e.g. 1.5 = 50% more XP
 */
public class XPCharmItem extends Item {

    private static final boolean TRINKETS_LOADED = FabricLoader.getInstance().isModLoaded("trinkets");

    public XPCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
        double mult = cfg.xpCharmMultiplier;

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("⭐ XP Charm").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Increases Battle XP by x" + mult)
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • Only affects battle XP")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  • Does NOT affect XP Candies")
                .withStyle(ChatFormatting.GRAY));
        
        if (!cfg.xpCharmEnabled) {
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("⚠ Currently disabled by server")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /**
     * Returns the XP multiplier for a player.
     * Reads from config: xpCharmEnabled and xpCharmMultiplier
     */
    public static double getXPMultiplier(ServerPlayer player) {
        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
        
        if (!cfg.xpCharmEnabled) {
            return 1.0;
        }
        
        if (hasXPCharm(player)) {
            EvolutionBoost.LOGGER.debug("[XPCharm] Player {} has XP Charm, applying x{}", 
                    player.getName().getString(), cfg.xpCharmMultiplier);
            return cfg.xpCharmMultiplier;
        }
        return 1.0;
    }

    /**
     * Checks if player has any XP Charm (permanent or 30d, inventory or trinkets).
     */
    public static boolean hasXPCharm(ServerPlayer player) {
        // Permanent charm in inventory
        if (hasXPCharmInInventory(player)) {
            return true;
        }
        
        // Timed charm in inventory
        if (TimedXPCharmItem.hasActiveTimedXPCharm(player)) {
            return true;
        }
        
        // Check trinkets
        if (TRINKETS_LOADED) {
            try {
                if (com.ichezzy.evolutionboost.compat.trinkets.TrinketsCompat.hasXPCharmTrinket(player)) {
                    return true;
                }
                if (com.ichezzy.evolutionboost.compat.trinkets.TrinketsCompat.hasActiveTimedXPCharm(player)) {
                    return true;
                }
            } catch (NoClassDefFoundError | Exception ignored) {}
        }
        
        return false;
    }

    /**
     * Checks if player has permanent XP Charm in inventory.
     */
    public static boolean hasXPCharmInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof XPCharmItem) {
                return true;
            }
        }
        return false;
    }
}
