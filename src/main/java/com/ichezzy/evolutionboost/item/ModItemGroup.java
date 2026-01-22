package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModItemGroup {
    private ModItemGroup() {}

    /** Main Tab for regular items */
    public static CreativeModeTab MAIN_TAB;

    /** Events Tab for seasonal items */
    public static CreativeModeTab EVENTS_TAB;

    /** Legacy compatibility */
    public static CreativeModeTab GROUP;

    public static void register() {
        // ==================== MAIN TAB ====================
        MAIN_TAB = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "main"),
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .icon(() -> new ItemStack(ModItems.EVOLUTION_COIN_PLATINUM))
                        .title(Component.translatable("itemGroup." + EvolutionBoost.MOD_ID + ".main"))
                        .displayItems((parameters, output) -> {
                            // ===== COINS =====
                            output.accept(ModItems.EVOLUTION_COIN_BRONZE);
                            output.accept(ModItems.EVOLUTION_COIN_SILVER);
                            output.accept(ModItems.EVOLUTION_COIN_GOLD);
                            output.accept(ModItems.EVOLUTION_COIN_PLATINUM);

                            // ===== TICKETS =====
                            output.accept(ModItems.SAFARI_TICKET);
                            output.accept(ModItems.HALLOWEEN_TICKET);

                            // ===== EQUIPMENT (Permanent) =====
                            output.accept(ModItems.RUNNING_SHOES);
                            output.accept(ModItems.GREAT_RUNNING_SHOES);
                            output.accept(ModItems.ULTRA_RUNNING_SHOES);
                            output.accept(ModItems.SHINY_CHARM);
                            output.accept(ModItems.XP_CHARM);

                            // ===== EQUIPMENT (30 Days) =====
                            output.accept(ModItems.RUNNING_SHOES_30D);
                            output.accept(ModItems.GREAT_RUNNING_SHOES_30D);
                            output.accept(ModItems.ULTRA_RUNNING_SHOES_30D);
                            output.accept(ModItems.SHINY_CHARM_30D);
                            output.accept(ModItems.XP_CHARM_30D);

                            // ===== SUPER EV MEDICINE =====
                            output.accept(ModItems.SUPER_MEDICINE);
                            output.accept(ModItems.SUPER_HP_UP);
                            output.accept(ModItems.SUPER_PROTEIN);
                            output.accept(ModItems.SUPER_IRON);
                            output.accept(ModItems.SUPER_CALCIUM);
                            output.accept(ModItems.SUPER_ZINC);
                            output.accept(ModItems.SUPER_CARBOS);
                            output.accept(ModItems.RESET_MEDICINE);

                            // ===== MINTS =====
                            output.accept(ModItems.UNIVERSAL_MINT);

                            // ===== BOTTLE CAPS =====
                            output.accept(ModItems.BOTTLE_CAP_COPPER);
                            output.accept(ModItems.BOTTLE_CAP_SILVER);
                            output.accept(ModItems.BOTTLE_CAP_GOLD);
                            output.accept(ModItems.BOTTLE_CAP_VOID);
                            output.accept(ModItems.BOTTLE_CAP_HP);
                            output.accept(ModItems.BOTTLE_CAP_ATK);
                            output.accept(ModItems.BOTTLE_CAP_DEF);
                            output.accept(ModItems.BOTTLE_CAP_SPATK);
                            output.accept(ModItems.BOTTLE_CAP_SPDEF);
                            output.accept(ModItems.BOTTLE_CAP_SPEED);

                            // ===== SWAPPERS =====
                            output.accept(ModItems.SHINY_SWAPPER);
                            output.accept(ModItems.GENDER_SWAPPER);
                            output.accept(ModItems.CAUGHT_BALL_SWAPPER);

                            // ===== EVENT VOUCHERS =====
                            output.accept(ModItems.EVENT_VOUCHER_BLANK);
                            output.accept(ModItems.EVENT_VOUCHER_IV);
                            output.accept(ModItems.EVENT_VOUCHER_XP);
                            output.accept(ModItems.EVENT_VOUCHER_SHINY);
                            output.accept(ModItems.EVENT_VOUCHER_EV);
                        })
                        .build()
        );

        // ==================== EVENTS TAB ====================
        EVENTS_TAB = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "events"),
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 1)
                        .icon(() -> new ItemStack(ModItems.HALLOWEEN_CANDY))
                        .title(Component.translatable("itemGroup." + EvolutionBoost.MOD_ID + ".events"))
                        .displayItems((parameters, output) -> {
                            // ===== HALLOWEEN =====
                            output.accept(ModItems.HALLOWEEN_CANDY);
                            output.accept(ModItems.HALLOWEEN_CANDY_BLUE);
                            output.accept(ModItems.HALLOWEEN_CANDY_PURPLE);
                            output.accept(ModItems.HALLOWEEN_CANDY_RED);
                            output.accept(ModItems.HALLOWEEN_BLOOD_VIAL);
                            output.accept(ModItems.HALLOWEEN_PUMPKIN_COOKIE);
                            output.accept(ModItems.HALLOWEEN_SKELETON_COOKIE);
                            output.accept(ModItems.HALLOWEEN_ZOMBIE_COOKIE);
                            output.accept(ModItems.HALLOWEEN_BUNDLE);
                            output.accept(ModItems.HALLOWEEN25_BRONZE);
                            output.accept(ModItems.HALLOWEEN25_SILVER);
                            output.accept(ModItems.HALLOWEEN25_GOLD);

                            // ===== CHRISTMAS =====
                            output.accept(ModItems.CANDY_CANE);
                            output.accept(ModItems.CANDY_CANE_GREEN);
                            output.accept(ModItems.CHRISTMAS_BALL_BLUE);
                            output.accept(ModItems.CHRISTMAS_BALL_GREEN);
                            output.accept(ModItems.CHRISTMAS_BALL_RED);
                            output.accept(ModItems.CHRISTMAS_BERRY);
                            output.accept(ModItems.CHRISTMAS_CANDY);
                            output.accept(ModItems.CHRISTMAS_CANDY_BLUE);
                            output.accept(ModItems.CHRISTMAS_CANDY_PURPLE);
                            output.accept(ModItems.CHRISTMAS_CANDY_PINK);
                            output.accept(ModItems.CHRISTMAS_HAT);
                            output.accept(ModItems.GRINCH_HAT);
                            output.accept(ModItems.CHRISTMAS_LOOT_SACK);
                            output.accept(ModItems.CHRISTMAS_SWEATER_BLUE);
                            output.accept(ModItems.CHRISTMAS_SWEATER_RED);
                            output.accept(ModItems.CHRISTMAS_TWIG);
                            output.accept(ModItems.CURSED_COAL);
                            output.accept(ModItems.CURSED_COAL_HEART);
                            output.accept(ModItems.CURSED_GIFT_BLACK);
                            output.accept(ModItems.CURSED_GIFT_PURPLE);
                            output.accept(ModItems.GIFT_BLUE);
                            output.accept(ModItems.GIFT_GREEN);
                            output.accept(ModItems.GIFT_RED);
                            output.accept(ModItems.GINGERBREAD);
                            output.accept(ModItems.HOLY_SPARK);
                            output.accept(ModItems.ICE_CROWN);
                            output.accept(ModItems.ICE_HEART);
                            output.accept(ModItems.LOST_TOY);
                            output.accept(ModItems.FROZEN_YETI_TOY);
                            output.accept(ModItems.RED_NOSE);
                            output.accept(ModItems.SPIRIT_DEW);
                            output.accept(ModItems.SPIRIT_DEW_SHARDS);
                            output.accept(ModItems.WIND_UP_KEY);
                            output.accept(ModItems.CHRISTMAS25_MEDAL);

                            // ===== BLOCKS =====
                            output.accept(ModItems.SPIRIT_ALTAR);
                        })
                        .build()
        );

        // Legacy compatibility
        GROUP = MAIN_TAB;
    }
}
