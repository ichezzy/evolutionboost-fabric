package com.ichezzy.evolutionboost.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimpleTooltipItem extends Item {
    private final @Nullable String tooltipKey;

    public SimpleTooltipItem(Item.Properties settings, @Nullable String tooltipKey) {
        super(settings);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendHoverText(ItemStack stack,
                                Item.TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        if (tooltipKey != null && !tooltipKey.isEmpty()) {
            tooltip.add(Component.translatable(tooltipKey));
        }
    }
}
