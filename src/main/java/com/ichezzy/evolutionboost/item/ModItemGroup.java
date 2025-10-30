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

                            // Halloween
                            output.accept(ModItems.HALLOWEEN_CANDY);
                            output.accept(ModItems.HALLOWEEN_BLOOD_VIAL);
                            output.accept(ModItems.HALLOWEEN_BUNDLE);
                            output.accept(ModItems.HALLOWEEN_TICKET);
                            output.accept(ModItems.HALLOWEEN25_BRONZE);
                            output.accept(ModItems.HALLOWEEN25_SILVER);
                            output.accept(ModItems.HALLOWEEN25_GOLD);

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
