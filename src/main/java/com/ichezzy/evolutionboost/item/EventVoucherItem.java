package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.boost.ActiveBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostScope;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.boost.BoostColors;
import com.ichezzy.evolutionboost.command.DurationParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Event Voucher - activates a global boost when used.
 * Uses the existing BoostManager system with bossbar display.
 */
public class EventVoucherItem extends Item {

    private final BoostType boostType;
    private final double multiplier;
    private final long durationMs;

    public EventVoucherItem(Properties properties, BoostType boostType, double multiplier, long durationMs) {
        super(properties);
        this.boostType = boostType;
        this.multiplier = multiplier;
        this.durationMs = durationMs;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        String icon = switch (boostType) {
            case IV -> "💎";
            case XP -> "⭐";
            case SHINY -> "✨";
            case EV -> "📈";
        };

        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal(icon + " " + boostType.name() + " Voucher")
                .withStyle(BoostColors.chatColor(boostType), ChatFormatting.BOLD));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Activates a server-wide boost:")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  • x" + multiplier + " " + boostType.name())
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal("  • " + DurationParser.pretty(durationMs) + " duration")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.literal(""));
        tooltip.add(Component.literal("Right-click to activate")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("⚠ Affects ALL players on the server")
                .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }

        // Check if there's already an active boost of this type
        BoostManager manager = BoostManager.get(serverPlayer.server);
        double currentMult = manager.getMultiplierFor(boostType, null);
        
        if (currentMult > 1.0) {
            serverPlayer.sendSystemMessage(Component.literal("✗ A " + boostType.name() + " boost is already active!")
                    .withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
        }

        // Activate the boost
        ActiveBoost boost = new ActiveBoost(boostType, BoostScope.GLOBAL, multiplier, durationMs);
        manager.addBoost(serverPlayer.server, boost);

        // Consume the item
        stack.shrink(1);

        // Broadcast to all players
        ChatFormatting color = BoostColors.chatColor(boostType);
        String icon = switch (boostType) {
            case IV -> "💎";
            case XP -> "⭐";
            case SHINY -> "✨";
            case EV -> "📈";
        };

        Component msg = Component.literal("[EvolutionBoost] ")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(serverPlayer.getName().getString())
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" activated ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(icon + " " + boostType.name() + " x" + multiplier)
                        .withStyle(color, ChatFormatting.BOLD))
                .append(Component.literal(" for " + DurationParser.pretty(durationMs))
                        .withStyle(ChatFormatting.GRAY));

        for (ServerPlayer p : serverPlayer.server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // Factory methods
    public static EventVoucherItem forIV(Properties properties) {
        return new EventVoucherItem(properties, BoostType.IV, 2.0, 3600000); // 1 hour
    }

    public static EventVoucherItem forXP(Properties properties) {
        return new EventVoucherItem(properties, BoostType.XP, 2.0, 3600000); // 1 hour
    }

    public static EventVoucherItem forShiny(Properties properties) {
        return new EventVoucherItem(properties, BoostType.SHINY, 2.0, 3600000); // 1 hour
    }

    public static EventVoucherItem forEV(Properties properties) {
        return new EventVoucherItem(properties, BoostType.EV, 2.0, 3600000); // 1 hour
    }
}
