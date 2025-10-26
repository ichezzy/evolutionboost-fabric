package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.item.Item;
import net.minecraft.item.Rarity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    private ModItems() {}

    // ---- Coins (nur Platzhalter) ----
    public static final Item EVOLUTION_COIN_BRONZE = register("evolution_coin_bronze",
            new SimpleTooltipItem(new Item.Settings().maxCount(999).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.coin_bronze"));

    public static final Item EVOLUTION_COIN_SILVER = register("evolution_coin_silver",
            new SimpleTooltipItem(new Item.Settings().maxCount(999).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.coin_silver"));

    public static final Item EVOLUTION_COIN_GOLD = register("evolution_coin_gold",
            new SimpleTooltipItem(new Item.Settings().maxCount(999).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.coin_gold"));

    // ---- Halloween Platzhalter ----
    public static final Item HALLOWEEN_CANDY = register("halloween_candy",
            new SimpleTooltipItem(new Item.Settings().maxCount(64).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.halloween_candy"));

    public static final Item HALLOWEEN_BLOOD_VIAL = register("halloween_blood_vial",
            new SimpleTooltipItem(new Item.Settings().maxCount(64).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.halloween_blood_vial"));

    public static final Item HALLOWEEN_BUNDLE = register("halloween_bundle",
            new SimpleTooltipItem(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.halloween_bundle"));

    // ---- Safari / Voucher (Platzhalter) ----
    public static final Item SHINY_CHARM = register("shiny_charm",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.shiny_charm"));

    public static final Item SAFARI_TICKET = register("safari_ticket",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.safari_ticket"));

    public static final Item EVENT_VOUCHER_BLANK = register("event_voucher_blank",
            new SimpleTooltipItem(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.voucher_blank"));

    public static final Item EVENT_VOUCHER_IV = register("event_voucher_iv",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_iv"));

    public static final Item EVENT_VOUCHER_XP = register("event_voucher_xp",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_xp"));

    public static final Item EVENT_VOUCHER_SHINY = register("event_voucher_shiny",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.voucher_shiny"));

    public static final Item EVENT_VOUCHER_DROP = register("event_voucher_drop",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_drop"));

    // optional
    public static final Item EVENT_VOUCHER_EV = register("event_voucher_ev",
            new SimpleTooltipItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_ev"));

    private static Item register(String path, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(EvolutionBoost.MOD_ID, path), item);
    }

    public static void registerAll() {
        EvolutionBoost.LOGGER.info("[{}] Items registered.", EvolutionBoost.MOD_ID);
    }
}
