package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Shiny Charm - increases shiny spawn chance in a radius around the player.
 */
public class ShinyCharmItem extends Item {

    private static final boolean TRINKETS_LOADED = FabricLoader.getInstance().isModLoaded("trinkets");

    public ShinyCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("✨ Shiny Charm").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));

        if (cfg.shinyCharmEnabled) {
            tooltip.add(Component.literal("Increases Shiny chance for nearby spawns")
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("  • x" + cfg.shinyCharmMultiplier + " multiplier")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("  • " + cfg.shinyCharmRadius + " block radius")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("Currently disabled")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // ==================== Static Helper ====================

    public static double getCharmMultiplierNear(ServerLevel level, net.minecraft.core.BlockPos spawnPos) {
        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();

        if (!cfg.shinyCharmEnabled) {
            return 1.0;
        }

        double radius = cfg.shinyCharmRadius > 0 ? cfg.shinyCharmRadius : 64.0;
        double multiplier = cfg.shinyCharmMultiplier > 0 ? cfg.shinyCharmMultiplier : 2.0;

        AABB searchBox = new AABB(
                spawnPos.getX() - radius, spawnPos.getY() - radius, spawnPos.getZ() - radius,
                spawnPos.getX() + radius, spawnPos.getY() + radius, spawnPos.getZ() + radius
        );

        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
                ServerPlayer.class,
                searchBox,
                player -> !player.isSpectator()
        );

        for (ServerPlayer player : nearbyPlayers) {
            if (hasShinyCharm(player)) {
                EvolutionBoost.LOGGER.debug(
                        "[ShinyCharm] Player {} has Shiny Charm near spawn at {}, applying x{}",
                        player.getName().getString(), spawnPos, multiplier);
                return multiplier;
            }
        }

        return 1.0;
    }

    /**
     * Checks if player has any Shiny Charm (permanent or 30d, inventory or trinkets).
     */
    public static boolean hasShinyCharm(ServerPlayer player) {
        // Permanent charm in inventory
        if (hasShinyCharmInInventory(player)) {
            return true;
        }
        
        // Timed charm in inventory
        if (TimedShinyCharmItem.hasActiveTimedShinyCharm(player)) {
            return true;
        }
        
        // Check trinkets
        if (TRINKETS_LOADED) {
            try {
                if (com.ichezzy.evolutionboost.compat.trinkets.TrinketsCompat.hasShinyCharmTrinket(player)) {
                    return true;
                }
                if (com.ichezzy.evolutionboost.compat.trinkets.TrinketsCompat.hasActiveTimedShinyCharm(player)) {
                    return true;
                }
            } catch (NoClassDefFoundError | Exception ignored) {}
        }
        
        return false;
    }

    public static boolean hasShinyCharmInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ShinyCharmItem) {
                return true;
            }
        }
        return false;
    }

    public static double getCharmMultiplierNear(Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return 1.0;
        }
        return getCharmMultiplierNear(serverLevel, entity.blockPosition());
    }
}
