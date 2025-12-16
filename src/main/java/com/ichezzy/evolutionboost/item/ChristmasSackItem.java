package com.ichezzy.evolutionboost.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Christmas Sack - Weihnachtliche Lootbox
 * Rechtsklick zum Öffnen.
 *
 * Garantiert 1x Common Roll.
 * Zusätzliche Chance auf:
 * - 30% Uncommon
 * - 10% Rare
 * - 2% Epic
 */
public class ChristmasSackItem extends Item {

    // Extra-Roll-Wahrscheinlichkeiten (nach 1 Common-Roll)
    private static final double EXTRA_UNCOMMON = 0.30;
    private static final double EXTRA_RARE     = 0.10;
    private static final double EXTRA_EPIC     = 0.02;

    public ChristmasSackItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) level;
            RandomSource rand = sp.getRandom();

            // 1 garantierter Common-Roll
            giveFromPool(sp, rand, buildCommonPool(rand));

            // optionaler Extra-Roll je nach Wahrscheinlichkeit
            double r = rand.nextDouble();
            if (r < EXTRA_EPIC) {
                giveFromPool(sp, rand, buildEpicPool());
            } else if (r < EXTRA_EPIC + EXTRA_RARE) {
                giveFromPool(sp, rand, buildRarePool(rand));
            } else if (r < EXTRA_EPIC + EXTRA_RARE + EXTRA_UNCOMMON) {
                giveFromPool(sp, rand, buildUncommonPool(rand));
            }

            // Festlicher Sound (Glocken + Geschenk-Sound)
            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0f, 1.2f);
            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.0f);

            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.awardStat(Stats.ITEM_USED.get(this));
            sp.getCooldowns().addCooldown(this, 10);
            sp.swing(hand, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /* ----------------- Pools ----------------- */

    /**
     * COMMON Pool - Basis-Loot (garantiert 1x)
     * Holy Spark, Gingerbread, Christmas Berry, Potions
     */
    private static List<Weighted> buildCommonPool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // Weihnachts-Währung (häufig)
        add(pool, () -> new ItemStack(ModItems.HOLY_SPARK, r(rand, 1, 5)), 35);
        add(pool, () -> new ItemStack(ModItems.GINGERBREAD, r(rand, 1, 3)), 25);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_BERRY, r(rand, 1, 3)), 20);

        // Basis-Items
        add(pool, () -> new ItemStack(Items.SNOWBALL, r(rand, 4, 16)), 10);
        add(pool, () -> stackOf("cobblemon", "potion", r(rand, 1, 3)), 10);

        return pool;
    }

    /**
     * UNCOMMON Pool - Geschenke, Cursed Items, Potions
     */
    private static List<Weighted> buildUncommonPool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // Geschenke
        add(pool, () -> new ItemStack(ModItems.GIFT_BLUE), 12);
        add(pool, () -> new ItemStack(ModItems.GIFT_GREEN), 12);
        add(pool, () -> new ItemStack(ModItems.GIFT_RED), 12);

        // Cursed Items (für Krampus-Theme)
        add(pool, () -> new ItemStack(ModItems.CURSED_COAL, r(rand, 1, 2)), 10);
        add(pool, () -> new ItemStack(ModItems.CURSED_GIFT_BLACK), 8);
        add(pool, () -> new ItemStack(ModItems.CURSED_GIFT_PURPLE), 8);

        // Cobblemon Items
        add(pool, () -> stackOf("cobblemon", "hyper_potion", r(rand, 1, 2)), 15);
        add(pool, () -> stackOf("cobblemon", "revive", r(rand, 1, 2)), 10);
        add(pool, () -> stackOf("cobblemon", "full_heal", r(rand, 1, 2)), 8);

        // SimpleHats Christmas
        add(pool, () -> stackOf("simplehats", "hatbag_christmas", 1), 5);

        return pool;
    }

    /**
     * RARE Pool - Sweater, Ice Heart, Spirit Dew Shards, Vitamins
     */
    private static List<Weighted> buildRarePool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // Christmas Kleidung
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_SWEATER_BLUE), 10);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_SWEATER_RED), 10);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_TWIG), 8);

        // Seltene Christmas Items
        add(pool, () -> new ItemStack(ModItems.ICE_HEART), 8);
        add(pool, () -> new ItemStack(ModItems.SPIRIT_DEW_SHARDS), 8);
        add(pool, () -> new ItemStack(ModItems.CURSED_COAL_HEART), 6);

        // Wertvolle Vanilla Items
        add(pool, () -> new ItemStack(Items.DIAMOND, r(rand, 1, 2)), 8);
        add(pool, () -> new ItemStack(Items.EMERALD, r(rand, 2, 4)), 10);
        add(pool, () -> new ItemStack(ModItems.EVOLUTION_COIN_BRONZE), 6);

        // Cobblemon Vitamins
        add(pool, () -> stackOf("cobblemon", "protein", 1), 4);
        add(pool, () -> stackOf("cobblemon", "calcium", 1), 4);
        add(pool, () -> stackOf("cobblemon", "iron", 1), 4);
        add(pool, () -> stackOf("cobblemon", "carbos", 1), 4);
        add(pool, () -> stackOf("cobblemon", "hp_up", 1), 4);
        add(pool, () -> stackOf("cobblemon", "zinc", 1), 4);
        add(pool, () -> stackOf("cobblemon", "pp_up", 1), 4);

        return pool;
    }

    /**
     * EPIC Pool - Beste Items, Raid Keys, Cosmetics
     */
    private static List<Weighted> buildEpicPool() {
        List<Weighted> pool = new ArrayList<>();

        // Christmas Cosmetics (sehr selten)
        add(pool, () -> new ItemStack(ModItems.CANDY_CANE), 8);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_HAT), 6);
        add(pool, () -> new ItemStack(ModItems.RED_NOSE), 5);
        add(pool, () -> new ItemStack(ModItems.ICE_CROWN), 3);

        // Christmas Balls
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_BALL_BLUE), 8);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_BALL_GREEN), 8);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_BALL_RED), 8);

        // Raid Keys (sehr wertvoll)
        add(pool, () -> new ItemStack(ModItems.ICE_QUEEN_RAID_KEY), 4);
        add(pool, () -> new ItemStack(ModItems.KRAMPUS_RAID_KEY), 4);

        // Legendäre Items
        add(pool, () -> new ItemStack(ModItems.SPIRIT_DEW), 3);
        add(pool, () -> new ItemStack(ModItems.LOST_TOY), 5);
        add(pool, () -> new ItemStack(ModItems.WIND_UP_KEY), 5);

        // Wertvolle Vanilla/Mod Items
        add(pool, () -> new ItemStack(Items.NETHERITE_INGOT), 2);
        add(pool, () -> new ItemStack(ModItems.EVOLUTION_COIN_SILVER), 4);

        // Cobblemon Best Items
        add(pool, () -> stackOf("cobblemon", "max_revive", 1), 5);
        add(pool, () -> stackOf("cobblemon", "full_restore", 1), 5);
        add(pool, () -> stackOf("cobblemon", "pp_max", 1), 4);
        add(pool, () -> stackOf("cobblemon", "rare_candy", 1), 6);

        return pool;
    }

    /* --------------- Ziehen & Helpers --------------- */

    private static void giveFromPool(ServerPlayer sp, RandomSource rand, List<Weighted> pool) {
        ItemStack drop = pickWeighted(pool, rand);
        if (!drop.isEmpty()) {
            if (!sp.getInventory().add(drop.copy())) sp.drop(drop.copy(), false);
        }
    }

    private static void add(List<Weighted> pool, Supplier<ItemStack> sup, int weight) {
        pool.add(new Weighted(() -> {
            ItemStack s = sup.get();
            return s == null ? ItemStack.EMPTY : s;
        }, weight));
    }

    private static ItemStack stackOf(String ns, String path, int count) {
        Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(ns, path));
        if (opt.isEmpty()) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(opt.get());
        s.setCount(Math.max(1, count));
        return s;
    }

    private static int r(RandomSource rand, int min, int max) {
        if (max <= min) return Math.max(1, min);
        return min + rand.nextInt(max - min + 1);
    }

    private record Weighted(Supplier<ItemStack> supplier, int weight) {}

    private static ItemStack pickWeighted(List<Weighted> list, RandomSource rand) {
        int total = 0;
        for (Weighted w : list) total += Math.max(0, w.weight);
        if (total <= 0) return ItemStack.EMPTY;
        int roll = rand.nextInt(total), acc = 0;
        for (Weighted w : list) {
            acc += Math.max(0, w.weight);
            if (roll < acc) {
                ItemStack s = w.supplier.get();
                return s == null ? ItemStack.EMPTY : s;
            }
        }
        return ItemStack.EMPTY;
    }
}