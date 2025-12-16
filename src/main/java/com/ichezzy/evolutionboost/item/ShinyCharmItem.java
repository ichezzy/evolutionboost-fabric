package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
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
 * Shiny Charm Item:
 * - Wenn ein Spieler dieses Item im Inventar hat, erhöht sich die Shiny-Chance
 *   für Pokémon-Spawns in seiner Nähe.
 * - Der Radius und Multiplikator sind in der Config einstellbar.
 */
public class ShinyCharmItem extends Item {

    public ShinyCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();

        tooltip.add(Component.literal("✨ Shiny Charm").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));

        if (cfg.shinyCharmEnabled) {
            tooltip.add(Component.literal("Increases Shiny chance for nearby spawns!")
                    .withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.literal("• Multiplier: x" + cfg.shinyCharmMultiplier)
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("• Radius: " + cfg.shinyCharmRadius + " blocks")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("Keep in your inventory to activate!")
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Currently disabled.")
                    .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // Glänzender Effekt wie bei verzauberten Items
        return true;
    }

    // ==================== Static Helper für ShinyHook ====================

    /**
     * Prüft, ob ein Spieler mit aktivem Shiny Charm in der Nähe der Position ist.
     *
     * @param level    Die ServerLevel in der der Spawn stattfindet
     * @param spawnPos Die Position des Spawns
     * @return Der Shiny Charm Multiplikator (1.0 wenn kein Charm in der Nähe)
     */
    public static double getCharmMultiplierNear(ServerLevel level, net.minecraft.core.BlockPos spawnPos) {
        EvolutionBoostConfig cfg = EvolutionBoostConfig.get();

        // Feature deaktiviert?
        if (!cfg.shinyCharmEnabled) {
            return 1.0;
        }

        double radius = cfg.shinyCharmRadius > 0 ? cfg.shinyCharmRadius : 64.0;
        double multiplier = cfg.shinyCharmMultiplier > 0 ? cfg.shinyCharmMultiplier : 2.0;

        // Suchbox um den Spawn-Punkt
        AABB searchBox = new AABB(
                spawnPos.getX() - radius, spawnPos.getY() - radius, spawnPos.getZ() - radius,
                spawnPos.getX() + radius, spawnPos.getY() + radius, spawnPos.getZ() + radius
        );

        // Alle Spieler in der Box finden
        List<ServerPlayer> nearbyPlayers = level.getEntitiesOfClass(
                ServerPlayer.class,
                searchBox,
                player -> !player.isSpectator()
        );

        // Prüfen ob einer davon den Shiny Charm hat
        for (ServerPlayer player : nearbyPlayers) {
            if (hasShinyCharmInInventory(player)) {
                EvolutionBoost.LOGGER.debug(
                        "[ShinyCharm] Player {} has Shiny Charm near spawn at {}, applying multiplier x{}",
                        player.getName().getString(),
                        spawnPos,
                        multiplier
                );
                return multiplier;
            }
        }

        return 1.0;
    }

    /**
     * Prüft ob der Spieler einen Shiny Charm im Inventar hat.
     */
    public static boolean hasShinyCharmInInventory(ServerPlayer player) {
        // Haupt-Inventar durchsuchen
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ShinyCharmItem) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prüft ob irgendein Spieler im Level einen Shiny Charm in der Nähe einer Entity hat.
     * Convenience-Methode für den ShinyHook.
     */
    public static double getCharmMultiplierNear(Entity entity) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return 1.0;
        }
        return getCharmMultiplierNear(serverLevel, entity.blockPosition());
    }
}