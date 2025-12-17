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
    public static CreativeModeTab GROUP;

    public static void register() {
        GROUP = Registry.register(
                BuiltInRegistries.CREATIVE_MODE_TAB,
                ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "main"),
                // 1.21+: Row + column erforderlich
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .icon(() -> new ItemStack(ModItems.EVOLUTION_COIN_GOLD))
                        .title(Component.translatable("itemGroup." + EvolutionBoost.MOD_ID + ".main"))
                        .displayItems((parameters, output) -> {
                            // Coins
                            output.accept(ModItems.EVOLUTION_COIN_BRONZE);
                            output.accept(ModItems.EVOLUTION_COIN_SILVER);
                            output.accept(ModItems.EVOLUTION_COIN_GOLD);
                            output.accept(ModItems.EVOLUTION_COIN_PLATINUM);

                            // Halloween
                            output.accept(ModItems.HALLOWEEN_CANDY);
                            output.accept(ModItems.HALLOWEEN_CANDY_BLUE);
                            output.accept(ModItems.HALLOWEEN_CANDY_PURPLE);
                            output.accept(ModItems.HALLOWEEN_CANDY_RED);
                            output.accept(ModItems.HALLOWEEN_BLOOD_VIAL);
                            output.accept(ModItems.HALLOWEEN_PUMPKIN_COOKIE);
                            output.accept(ModItems.HALLOWEEN_SKELETON_COOKIE);
                            output.accept(ModItems.HALLOWEEN_ZOMBIE_COOKIE);
                            output.accept(ModItems.HALLOWEEN_BUNDLE);
                            output.accept(ModItems.HALLOWEEN_TICKET);
                            output.accept(ModItems.HALLOWEEN25_BRONZE);
                            output.accept(ModItems.HALLOWEEN25_SILVER);
                            output.accept(ModItems.HALLOWEEN25_GOLD);

                            // Christmas
                            output.accept(ModItems.CANDY_CANE);
                            output.accept(ModItems.CHRISTMAS_BALL_BLUE);
                            output.accept(ModItems.CHRISTMAS_BALL_GREEN);
                            output.accept(ModItems.CHRISTMAS_BALL_RED);
                            output.accept(ModItems.CHRISTMAS_BERRY);
                            output.accept(ModItems.CHRISTMAS_CANDY);
                            output.accept(ModItems.CHRISTMAS_HAT);
                            output.accept(ModItems.CHRISTMAS_LOOT_SACK);
                            output.accept(ModItems.CHRISTMAS_SWEATER_BLUE);
                            output.accept(ModItems.CHRISTMAS_SWEATER_RED);
                            output.accept(ModItems.CHRISTMAS_TWIG);
                            output.accept(ModItems.CURSED_COAL);
                            output.accept(ModItems.CURSED_COAL_HEART);
                            output.accept(ModItems.CURSED_GIFT_BLACK);
                            output.accept(ModItems.CURSED_GIFT_PURPLE);
                            output.accept(ModItems.FROZEN_YETI_TOY);
                            output.accept(ModItems.GIFT_BLUE);
                            output.accept(ModItems.GIFT_GREEN);
                            output.accept(ModItems.GIFT_RED);
                            output.accept(ModItems.GINGERBREAD);
                            output.accept(ModItems.GRINCH_HAT);
                            output.accept(ModItems.HOLY_SPARK);
                            output.accept(ModItems.ICE_CROWN);
                            output.accept(ModItems.ICE_HEART);
                            output.accept(ModItems.LOST_TOY);
                            output.accept(ModItems.RED_NOSE);
                            output.accept(ModItems.SPIRIT_DEW);
                            output.accept(ModItems.SPIRIT_DEW_SHARDS);
                            output.accept(ModItems.WIND_UP_KEY);

                            // Tickets & Voucher
                            output.accept(ModItems.SAFARI_TICKET);
                            output.accept(ModItems.SHINY_CHARM);
                            output.accept(ModItems.EVENT_VOUCHER_BLANK);
                            output.accept(ModItems.EVENT_VOUCHER_IV);
                            output.accept(ModItems.EVENT_VOUCHER_XP);
                            output.accept(ModItems.EVENT_VOUCHER_SHINY);
                            output.accept(ModItems.EVENT_VOUCHER_DROP);
                            output.accept(ModItems.EVENT_VOUCHER_EV);
                        })
                        .build()
        );
    }
}
