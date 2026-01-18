package com.ichezzy.evolutionboost.compat.trinkets;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.item.*;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trinkets integration for all EvolutionBoost equipable items.
 */
public final class TrinketsCompat {
    private TrinketsCompat() {}

    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            // === Charms (Necklace Slot) ===
            TrinketsApi.registerTrinket(ModItems.SHINY_CHARM, new CharmTrinket(SoundEvents.AMETHYST_BLOCK_CHIME));
            TrinketsApi.registerTrinket(ModItems.SHINY_CHARM_30D, new CharmTrinket(SoundEvents.AMETHYST_BLOCK_CHIME));
            TrinketsApi.registerTrinket(ModItems.XP_CHARM, new CharmTrinket(SoundEvents.EXPERIENCE_ORB_PICKUP));
            TrinketsApi.registerTrinket(ModItems.XP_CHARM_30D, new CharmTrinket(SoundEvents.EXPERIENCE_ORB_PICKUP));

            // === Running Shoes (Shoes Slot) - Permanent ===
            TrinketsApi.registerTrinket(ModItems.RUNNING_SHOES, 
                    new RunningBootsTrinket(RunningBootsItem.Tier.NORMAL, false));
            TrinketsApi.registerTrinket(ModItems.GREAT_RUNNING_SHOES, 
                    new RunningBootsTrinket(RunningBootsItem.Tier.GREAT, false));
            TrinketsApi.registerTrinket(ModItems.ULTRA_RUNNING_SHOES, 
                    new RunningBootsTrinket(RunningBootsItem.Tier.ULTRA, false));
            
            // === Running Shoes (Shoes Slot) - 30 Days ===
            TrinketsApi.registerTrinket(ModItems.RUNNING_SHOES_30D, 
                    new RunningBootsTrinket(RunningBootsItem.Tier.NORMAL, true));
            TrinketsApi.registerTrinket(ModItems.GREAT_RUNNING_SHOES_30D, 
                    new RunningBootsTrinket(RunningBootsItem.Tier.GREAT, true));
            TrinketsApi.registerTrinket(ModItems.ULTRA_RUNNING_SHOES_30D, 
                    new RunningBootsTrinket(RunningBootsItem.Tier.ULTRA, true));

            EvolutionBoost.LOGGER.info("[TrinketsCompat] Registered all trinkets successfully");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[TrinketsCompat] Failed to initialize: {}", e.getMessage());
        }
    }

    // ==================== Public Helper Methods ====================

    public static boolean hasTrinketEquipped(Player player, Item item) {
        try {
            Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(player);
            if (component.isEmpty()) return false;
            return component.get().isEquipped(item);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasShinyCharmTrinket(Player player) {
        return hasTrinketEquipped(player, ModItems.SHINY_CHARM);
    }

    public static boolean hasXPCharmTrinket(Player player) {
        return hasTrinketEquipped(player, ModItems.XP_CHARM);
    }

    public static boolean hasActiveTimedShinyCharm(Player player) {
        try {
            Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(player);
            if (component.isEmpty()) return false;

            AtomicBoolean found = new AtomicBoolean(false);
            component.get().forEach((slotRef, stack) -> {
                if (stack.getItem() instanceof TimedShinyCharmItem timedCharm) {
                    if (timedCharm.isActive(stack)) {
                        found.set(true);
                    }
                }
            });
            return found.get();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasActiveTimedXPCharm(Player player) {
        try {
            Optional<TrinketComponent> component = TrinketsApi.getTrinketComponent(player);
            if (component.isEmpty()) return false;

            AtomicBoolean found = new AtomicBoolean(false);
            component.get().forEach((slotRef, stack) -> {
                if (stack.getItem() instanceof TimedXPCharmItem timedCharm) {
                    if (timedCharm.isActive(stack)) {
                        found.set(true);
                    }
                }
            });
            return found.get();
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Trinket Implementations ====================

    /** Generic Charm - no special effect on equip */
    private static class CharmTrinket implements Trinket {
        private final SoundEvent equipSound;

        public CharmTrinket(SoundEvent equipSound) {
            this.equipSound = equipSound;
        }

        @Override
        public Holder<SoundEvent> getEquipSound(ItemStack stack, SlotReference slot, LivingEntity entity) {
            return Holder.direct(equipSound);
        }
    }

    /** Running Boots - applies speed modifier when equipped */
    private static class RunningBootsTrinket implements Trinket {
        private final RunningBootsItem.Tier tier;
        private final boolean isTimed;
        private final ResourceLocation modifierId;

        public RunningBootsTrinket(RunningBootsItem.Tier tier, boolean isTimed) {
            this.tier = tier;
            this.isTimed = isTimed;
            this.modifierId = ResourceLocation.fromNamespaceAndPath(
                    EvolutionBoost.MOD_ID,
                    "trinket_speed_" + tier.name().toLowerCase() + (isTimed ? "_30d" : "")
            );
        }

        @Override
        public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            if (isTimed && stack.getItem() instanceof TimedRunningBootsItem timedItem) {
                if (!timedItem.isActive(stack)) return;
            }

            AttributeInstance speedAttribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttribute != null && !speedAttribute.hasModifier(modifierId)) {
                speedAttribute.addPermanentModifier(new AttributeModifier(
                        modifierId,
                        tier.getSpeedBonus(),
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        }

        @Override
        public void onUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
            AttributeInstance speedAttribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.removeModifier(modifierId);
            }
        }

        @Override
        public void tick(ItemStack stack, SlotReference slot, LivingEntity entity) {
            if (isTimed && stack.getItem() instanceof TimedRunningBootsItem timedItem) {
                AttributeInstance speedAttribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
                if (speedAttribute == null) return;
                
                if (timedItem.isExpired(stack)) {
                    speedAttribute.removeModifier(modifierId);
                } else if (!speedAttribute.hasModifier(modifierId)) {
                    speedAttribute.addPermanentModifier(new AttributeModifier(
                            modifierId,
                            tier.getSpeedBonus(),
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
            }
        }

        @Override
        public Holder<SoundEvent> getEquipSound(ItemStack stack, SlotReference slot, LivingEntity entity) {
            return Holder.direct(SoundEvents.ARMOR_EQUIP_LEATHER.value());
        }
    }
}
