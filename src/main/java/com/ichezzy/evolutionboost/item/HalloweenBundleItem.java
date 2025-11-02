package com.ichezzy.evolutionboost.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public class HalloweenBundleItem extends Item {

    // Neuer, eindeutiger Pfad: data/evolutionboost/loot_tables/halloween_bundle.json
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath("evolutionboost", "halloween_bundle")
    );

    public HalloweenBundleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) level;

            LootTable table = sl.getServer().reloadableRegistries().getLootTable(LOOT);
            if (table == LootTable.EMPTY) {
                // Zur Diagnose kurz Audio-Feedback geben:
                sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                        SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0f, 1.0f);
                return InteractionResultHolder.fail(stack);
            }

            LootParams params = new LootParams.Builder(sl)
                    .withParameter(LootContextParams.THIS_ENTITY, sp)
                    .withParameter(LootContextParams.ORIGIN, sp.position())
                    .withParameter(LootContextParams.TOOL, stack)
                    .withLuck(sp.getLuck())
                    .create(LootContextParamSets.GIFT); // passt zu type:"minecraft:gift"

            // Overload OHNE Random
            List<ItemStack> drops = table.getRandomItems(params);

            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                ItemStack copy = drop.copy();
                if (!sp.getInventory().add(copy)) {
                    sp.drop(copy, false);
                }
            }

            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0f, 1.0f);

            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.awardStat(Stats.ITEM_USED.get(this));
            sp.getCooldowns().addCooldown(this, 10);
            sp.swing(hand, true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
