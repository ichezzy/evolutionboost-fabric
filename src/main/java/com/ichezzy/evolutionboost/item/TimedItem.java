package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Base class for items with expiration timer.
 * Timer activates automatically on first pickup.
 */
public abstract class TimedItem extends Item {
    
    protected final int durationDays;
    
    public TimedItem(Properties properties, int durationDays) {
        super(properties);
        this.durationDays = durationDays;
    }
    
    public long getExpirationTime(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return -1;
        
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("ExpiresAt")) return -1;
        
        return tag.getLong("ExpiresAt");
    }
    
    public void activateTimer(ItemStack stack) {
        long expiresAt = Instant.now().plus(durationDays, ChronoUnit.DAYS).toEpochMilli();
        
        CompoundTag tag = new CompoundTag();
        CustomData existingData = stack.get(DataComponents.CUSTOM_DATA);
        if (existingData != null) {
            tag = existingData.copyTag();
        }
        tag.putLong("ExpiresAt", expiresAt);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    
    public boolean isExpired(ItemStack stack) {
        long expiresAt = getExpirationTime(stack);
        if (expiresAt <= 0) return false;
        return System.currentTimeMillis() > expiresAt;
    }
    
    public boolean isActive(ItemStack stack) {
        long expiresAt = getExpirationTime(stack);
        if (expiresAt <= 0) return false;
        return System.currentTimeMillis() <= expiresAt;
    }
    
    public String getRemainingTimeFormatted(ItemStack stack) {
        long expiresAt = getExpirationTime(stack);
        if (expiresAt <= 0) return "Not activated";
        
        long remainingMs = expiresAt - System.currentTimeMillis();
        if (remainingMs <= 0) return "Expired";
        
        long seconds = remainingMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        hours = hours % 24;
        minutes = minutes % 60;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }
    
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        
        if (!level.isClientSide() && entity instanceof Player) {
            if (getExpirationTime(stack) <= 0) {
                activateTimer(stack);
                EvolutionBoost.LOGGER.debug("[TimedItem] Activated {} ({} days)", 
                        stack.getItem(), durationDays);
            }
        }
    }
    
    protected void appendTimerTooltip(ItemStack stack, List<Component> tooltip) {
        long expiresAt = getExpirationTime(stack);
        
        if (expiresAt <= 0) {
            tooltip.add(Component.literal("⏱ " + durationDays + " days (activates on pickup)")
                    .withStyle(ChatFormatting.YELLOW));
        } else if (isExpired(stack)) {
            tooltip.add(Component.literal("⏱ EXPIRED")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        } else {
            tooltip.add(Component.literal("⏱ " + getRemainingTimeFormatted(stack) + " remaining")
                    .withStyle(ChatFormatting.GREEN));
        }
    }
    
    public int getDurationDays() {
        return durationDays;
    }
}
