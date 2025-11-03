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
 * LuckyBox-Style: reiner Code-Zufall (keine Loot Tables).
 * Raritäten + Mengen gemäss Vorgabe:
 *
 * Common (Gewicht 20 pro Eintrag):
 *  - Blood Vial (1-5)
 *  - Iron Ingot (3-5)
 *  - Hyper Potion (1-2)             -> cobblemon:hyper_potion
 *  - Candy blau (3-5)               -> ModItems.HALLOWEEN_CANDY_BLUE
 *
 * Uncommon (Gewicht 10 pro Eintrag; Menge 1-3, ausser Hatbag fix 1):
 *  - Candy lila (1-3)               -> ModItems.HALLOWEEN_CANDY_PURPLE
 *  - Gold Ingot (1-3)
 *  - Revive (1-3)                   -> cobblemon:revive
 *  - Hatbag Halloween (x1)          -> simplehats:hatbag_halloween
 *  - Full Heal (1-3)                -> cobblemon:full_heal
 *
 * Rare (Gewicht 4 pro Eintrag; i. d. R. x1; Diamond & Candy rot 1-3):
 *  - Max Potion (x1)                -> cobblemon:max_potion
 *  - Max Ether (x1)                 -> cobblemon:max_ether
 *  - Max Elixir (x1)                -> cobblemon:max_elixir
 *  - Diamond (1-3)
 *  - Evolution Coin Bronze (x1)     -> ModItems.EVOLUTION_COIN_BRONZE
 *  - PP Up (x1)                      -> cobblemon:pp_up
 *  - Protein (x1)                    -> cobblemon:protein
 *  - Calcium (x1)                    -> cobblemon:calcium
 *  - Iron (x1)                       -> cobblemon:iron
 *  - Carbos (x1)                     -> cobblemon:carbos
 *  - HP Up (x1)                      -> cobblemon:hp_up
 *  - Zinc (x1)                       -> cobblemon:zinc
 *  - Candy rot (1-3)                 -> ModItems.HALLOWEEN_CANDY_RED
 *
 * Epic (Gewicht 1 pro Eintrag; alle x1):
 *  - Max Revive                      -> cobblemon:max_revive
 *  - Halloween Ticket                -> ModItems.HALLOWEEN_TICKET
 *  - Halloween Candy normal          -> ModItems.HALLOWEEN_CANDY
 *  - Netherite Ingot                 -> minecraft:netherite_ingot
 *  - Full Restore                    -> cobblemon:full_restore
 *  - PP Max                          -> cobblemon:pp_max
 *
 * Zwei Rolls pro Nutzung (wie zuvor).
 */
public class HalloweenBundleItem extends Item {

    public HalloweenBundleItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ServerLevel sl = (ServerLevel) level;
            RandomSource rand = sp.getRandom();

            // zwei unabhängige Rolls
            giveOneRoll(sp, rand);
            giveOneRoll(sp, rand);

            sl.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0f, 1.0f);
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            sp.awardStat(Stats.ITEM_USED.get(this));
            sp.getCooldowns().addCooldown(this, 10);
            sp.swing(hand, true);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Ein Roll: baut den gewichteten Pool und gibt 1 Eintrag */
    private static void giveOneRoll(ServerPlayer sp, RandomSource rand) {
        List<Weighted> pool = new ArrayList<>();

        // ===== Common (Gewicht 20) =====
        add(pool, () -> new ItemStack(ModItems.HALLOWEEN_BLOOD_VIAL, r(rand, 1, 5)), 20);
        add(pool, () -> new ItemStack(Items.IRON_INGOT, r(rand, 3, 5)), 20);
        add(pool, () -> stackOf("cobblemon", "hyper_potion", r(rand, 1, 2)), 20);
        add(pool, () -> new ItemStack(ModItems.HALLOWEEN_CANDY_BLUE, r(rand, 3, 5)), 20);

        // ===== Uncommon (Gewicht 10); 1-3, ausser Hatbag = 1 =====
        add(pool, () -> new ItemStack(ModItems.HALLOWEEN_CANDY_PURPLE, r(rand, 1, 3)), 10);
        add(pool, () -> new ItemStack(Items.GOLD_INGOT, r(rand, 1, 3)), 10);
        add(pool, () -> stackOf("cobblemon", "revive", r(rand, 1, 3)), 10);
        add(pool, () -> stackOf("simplehats", "hatbag_halloween", 1), 10);
        add(pool, () -> stackOf("cobblemon", "full_heal", r(rand, 1, 3)), 10);

        // ===== Rare (Gewicht 4); x1, ausser Diamond/Candy rot = 1-3 =====
        add(pool, () -> stackOf("cobblemon", "max_potion", 1), 4);
        add(pool, () -> stackOf("cobblemon", "max_ether", 1), 4);
        add(pool, () -> stackOf("cobblemon", "max_elixir", 1), 4);
        add(pool, () -> new ItemStack(Items.DIAMOND, r(rand, 1, 3)), 4);
        add(pool, () -> new ItemStack(ModItems.EVOLUTION_COIN_BRONZE), 4);
        add(pool, () -> stackOf("cobblemon", "pp_up", 1), 4);
        add(pool, () -> stackOf("cobblemon", "protein", 1), 4);
        add(pool, () -> stackOf("cobblemon", "calcium", 1), 4);
        add(pool, () -> stackOf("cobblemon", "iron", 1), 4);
        add(pool, () -> stackOf("cobblemon", "carbos", 1), 4);
        add(pool, () -> stackOf("cobblemon", "hp_up", 1), 4);
        add(pool, () -> stackOf("cobblemon", "zinc", 1), 4);
        add(pool, () -> new ItemStack(ModItems.HALLOWEEN_CANDY_RED, r(rand, 1, 3)), 4);

        // ===== Epic (Gewicht 1); alle x1 =====
        add(pool, () -> stackOf("cobblemon", "max_revive", 1), 1);
        add(pool, () -> new ItemStack(ModItems.HALLOWEEN_TICKET), 1);
        add(pool, () -> new ItemStack(ModItems.HALLOWEEN_CANDY), 1);
        add(pool, () -> new ItemStack(Items.NETHERITE_INGOT), 1);
        add(pool, () -> stackOf("cobblemon", "full_restore", 1), 1);
        add(pool, () -> stackOf("cobblemon", "pp_max", 1), 1);

        // Ziehen & geben
        ItemStack drop = pickWeighted(pool, rand);
        if (!drop.isEmpty()) {
            if (!sp.getInventory().add(drop.copy())) {
                sp.drop(drop.copy(), false);
            }
        }
    }

    /* ---------- Helpers ---------- */

    private static void add(List<Weighted> pool, Supplier<ItemStack> sup, int weight) {
        // Eintrag nur hinzufügen, wenn das Ziel-Item existiert (bei Fremdmods)
        pool.add(new Weighted(() -> {
            ItemStack s = sup.get();
            return s == null ? ItemStack.EMPTY : s;
        }, weight));
    }

    private static ItemStack stackOf(String ns, String path, int count) {
        Optional<Item> opt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(ns, path));
        if (opt.isEmpty()) return ItemStack.EMPTY; // Mod fehlt o. Item unbekannt -> Eintrag wird ignoriert
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

        int roll = rand.nextInt(total);
        int acc = 0;
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
