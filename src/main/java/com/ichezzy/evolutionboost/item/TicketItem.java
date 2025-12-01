package com.ichezzy.evolutionboost.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class TicketItem extends Item {
    private final TicketManager.Target target;
    private final long durationTicks; // z.B. 3600 * 20 = 1h

    public TicketItem(Properties props, TicketManager.Target target, long durationTicks) {
        super(props);
        this.target = target;
        this.durationTicks = durationTicks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (level.isClientSide) return InteractionResultHolder.pass(player.getItemInHand(hand));

        if (player instanceof ServerPlayer sp) {
            boolean ok = TicketManager.startTicket(sp, target, durationTicks);
            if (ok) {
                // Item verbrauchen
                ItemStack stack = player.getItemInHand(hand);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
            } else {
                // bereits Session aktiv
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}
