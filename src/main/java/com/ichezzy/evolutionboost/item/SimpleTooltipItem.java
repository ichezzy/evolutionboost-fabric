package com.ichezzy.evolutionboost.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SimpleTooltipItem extends Item {
    private final @Nullable String tooltipKey;

    public SimpleTooltipItem(Settings settings, @Nullable String tooltipKey) {
        super(settings);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        if (tooltipKey != null) {
            tooltip.add(Text.translatable(tooltipKey));
        }
    }
}
