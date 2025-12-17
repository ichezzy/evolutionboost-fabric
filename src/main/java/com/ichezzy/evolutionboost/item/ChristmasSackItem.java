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

    private static final double EXTRA_UNCOMMON = 0.35;
    private static final double EXTRA_RARE     = 0.15;
    private static final double EXTRA_EPIC     = 0.05;

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
                giveFromPool(sp, rand, buildEpicPool(rand));
            } else if (r < EXTRA_EPIC + EXTRA_RARE) {
                giveFromPool(sp, rand, buildRarePool(rand));
            } else if (r < EXTRA_EPIC + EXTRA_RARE + EXTRA_UNCOMMON) {
                giveFromPool(sp, rand, buildUncommonPool(rand));
            }

            // Festlicher Sound
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

    /* ======================= COMMON POOL ======================= */
    private static List<Weighted> buildCommonPool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // EvolutionBoost Items (Common)
        add(pool, () -> new ItemStack(ModItems.GINGERBREAD, r(rand, 1, 3)), 25);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_CANDY_BLUE, r(rand, 2, 5)), 25);
        add(pool, () -> new ItemStack(ModItems.HOLY_SPARK, r(rand, 1, 5)), 25);

        // Minecraft Items (Common)
        add(pool, () -> new ItemStack(Items.IRON_INGOT, r(rand, 2, 5)), 12);
        add(pool, () -> new ItemStack(Items.COPPER_INGOT, r(rand, 3, 8)), 8);

        // Cobblemon Items (Common)
        add(pool, () -> stackOf("cobblemon", "hyper_potion", r(rand, 1, 2)), 5);

        return pool;
    }

    /* ======================= UNCOMMON POOL ======================= */
    private static List<Weighted> buildUncommonPool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // EvolutionBoost Items (Uncommon)
        add(pool, () -> new ItemStack(ModItems.EVOLUTION_COIN_BRONZE, r(rand, 1, 2)), 15);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_CANDY, r(rand, 1, 3)), 15);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_CANDY_PURPLE, r(rand, 1, 3)), 15);

        // Minecraft Items (Uncommon)
        add(pool, () -> new ItemStack(Items.GOLD_INGOT, r(rand, 1, 3)), 12);

        // Cobblemon Items (Uncommon)
        add(pool, () -> stackOf("cobblemon", "revive", r(rand, 1, 2)), 12);
        add(pool, () -> stackOf("cobblemon", "full_heal", r(rand, 1, 2)), 10);
        add(pool, () -> stackOf("cobblemon", "exp_candy_s", r(rand, 1, 3)), 10);
        add(pool, () -> stackOf("cobblemon", "exp_candy_m", r(rand, 1, 2)), 8);

        // SimpleHats Christmas
        add(pool, () -> stackOf("simplehats", "hatbag_christmas", 1), 3);

        return pool;
    }

    /* ======================= RARE POOL ======================= */
    private static List<Weighted> buildRarePool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // EvolutionBoost Items (Rare)
        add(pool, () -> new ItemStack(ModItems.EVOLUTION_COIN_SILVER), 8);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_CANDY_PINK, r(rand, 1, 2)), 10);
        add(pool, () -> new ItemStack(ModItems.SPIRIT_DEW_SHARDS), 8);
        add(pool, () -> new ItemStack(ModItems.RED_NOSE), 6);
        add(pool, () -> new ItemStack(ModItems.GIFT_GREEN), 8);

        // Minecraft Items (Rare)
        add(pool, () -> new ItemStack(Items.DIAMOND, r(rand, 1, 2)), 8);

        // Cobblemon Items (Rare)
        add(pool, () -> stackOf("cobblemon", "max_potion", 1), 6);
        add(pool, () -> stackOf("cobblemon", "max_ether", 1), 6);
        add(pool, () -> stackOf("cobblemon", "max_elixir", 1), 6);
        add(pool, () -> stackOf("cobblemon", "exp_candy_l", r(rand, 1, 2)), 6);

        // Cobblemon Vitamins (Rare)
        add(pool, () -> stackOf("cobblemon", "protein", 1), 4);
        add(pool, () -> stackOf("cobblemon", "calcium", 1), 4);
        add(pool, () -> stackOf("cobblemon", "iron", 1), 4);
        add(pool, () -> stackOf("cobblemon", "carbos", 1), 4);
        add(pool, () -> stackOf("cobblemon", "hp_up", 1), 4);
        add(pool, () -> stackOf("cobblemon", "zinc", 1), 4);

        return pool;
    }

    /* ======================= EPIC POOL ======================= */
    private static List<Weighted> buildEpicPool(RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // EvolutionBoost Items (Epic)
        add(pool, () -> new ItemStack(ModItems.EVOLUTION_COIN_GOLD), 6);
        add(pool, () -> new ItemStack(ModItems.GRINCH_HAT), 5);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_SWEATER_RED), 6);
        add(pool, () -> new ItemStack(ModItems.FROZEN_YETI_TOY), 5);
        add(pool, () -> new ItemStack(ModItems.CHRISTMAS_BALL_GREEN), 6);
        add(pool, () -> new ItemStack(ModItems.EVENT_VOUCHER_BLANK), 4);

        // Minecraft Items (Epic)
        add(pool, () -> new ItemStack(Items.NETHERITE_SCRAP, r(rand, 1, 2)), 5);

        // Cobblemon Items (Epic)
        add(pool, () -> stackOf("cobblemon", "max_revive", 1), 8);
        add(pool, () -> stackOf("cobblemon", "full_restore", 1), 8);
        add(pool, () -> stackOf("cobblemon", "pp_max", 1), 5);
        add(pool, () -> stackOf("cobblemon", "exp_candy_xl", r(rand, 1, 2)), 6);
        add(pool, () -> stackOf("cobblemon", "rare_candy", 1), 4);

        return pool;
    }

    /* ======================= HELPERS ======================= */

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