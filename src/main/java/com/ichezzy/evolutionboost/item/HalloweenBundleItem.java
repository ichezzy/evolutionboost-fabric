package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.core.registries.Registries;

import java.util.List;

/**
 * Right-click to roll a loot table and consume the bundle.
 * Loot table JSON lives at:
 *   data/evolutionboost/loot_tables/items/halloween_bundle.json
 */
public class HalloweenBundleItem extends Item {

    /** evolutionboost:items/halloween_bundle as a loot-table key */
    private static final ResourceKey<LootTable> LOOT =
            ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "items/halloween_bundle")
            );

    public HalloweenBundleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!(level instanceof ServerLevel serverLevel)) {
            // client: play a tiny hand animation, let server do the real work
            return InteractionResultHolder.success(stack);
        }

        // fetch loot table from registry
        LootTable table = serverLevel.getServer()
                .reloadableRegistries()
                .getLootTable(LOOT);

        // if the table is missing, just fail gracefully
        if (table == null) {
            EvolutionBoost.LOGGER.warn("[halloween_bundle] loot table not found: {}", LOOT.location());
            return InteractionResultHolder.fail(stack);
        }

        // build loot params
        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);

        // generate drops
        List<ItemStack> drops = table.getRandomItems(params);

        // give to player (falls Inventar voll ist: wird vor dem Spieler gedroppt)
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                player.getInventory().placeItemBackInInventory(drop);
            }
        }

        // consume one bundle
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.awardStat(Stats.ITEM_USED.get(this));

        // small pop sound
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.8f, 1.0f);

        return InteractionResultHolder.success(stack);
    }
}
