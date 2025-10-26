package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ModItemGroup {
    private ModItemGroup() {}
    public static ItemGroup GROUP;

    public static void register() {
        GROUP = Registry.register(
                Registries.ITEM_GROUP,
                new Identifier(EvolutionBoost.MOD_ID, "main"),
                FabricItemGroup.builder()
                        .icon(() -> new ItemStack(ModItems.EVOLUTION_COIN_GOLD))
                        .displayName(Text.translatable("itemGroup." + EvolutionBoost.MOD_ID + ".main"))
                        .entries((ctx, entries) -> {
                            entries.add(ModItems.EVOLUTION_COIN_BRONZE);
                            entries.add(ModItems.EVOLUTION_COIN_SILVER);
                            entries.add(ModItems.EVOLUTION_COIN_GOLD);

                            entries.add(ModItems.HALLOWEEN_CANDY);
                            entries.add(ModItems.HALLOWEEN_BLOOD_VIAL);
                            entries.add(ModItems.HALLOWEEN_BUNDLE);

                            entries.add(ModItems.SAFARI_TICKET);

                            entries.add(ModItems.SHINY_CHARM);
                            entries.add(ModItems.EVENT_VOUCHER_BLANK);
                            entries.add(ModItems.EVENT_VOUCHER_IV);
                            entries.add(ModItems.EVENT_VOUCHER_XP);
                            entries.add(ModItems.EVENT_VOUCHER_SHINY);
                            entries.add(ModItems.EVENT_VOUCHER_DROP);
                            entries.add(ModItems.EVENT_VOUCHER_EV);
                        })
                        .build()
        );
    }
}
