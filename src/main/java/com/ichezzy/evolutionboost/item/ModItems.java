package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.ticket.TicketManager;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    private ModItems() {}

    // ---- Coins (nur Platzhalter) ----
    public static final Item EVOLUTION_COIN_BRONZE = register(
            "evolution_coin_bronze",
            new SimpleTooltipItem(new Item.Properties().stacksTo(999).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.coin_bronze")
    );

    public static final Item EVOLUTION_COIN_SILVER = register(
            "evolution_coin_silver",
            new SimpleTooltipItem(new Item.Properties().stacksTo(999).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.coin_silver")
    );

    public static final Item EVOLUTION_COIN_GOLD = register(
            "evolution_coin_gold",
            new SimpleTooltipItem(new Item.Properties().stacksTo(999).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.coin_gold")
    );

    // ---- Halloween Platzhalter ----
    public static final Item HALLOWEEN_CANDY = register(
            "halloween_candy",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.halloween_candy")
    );

    public static final Item HALLOWEEN_CANDY_BLUE = register(
            "halloween_candy_blue",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.halloween_candy_blue")
    );

    public static final Item HALLOWEEN_CANDY_PURPLE = register(
            "halloween_candy_purple",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.halloween_candy_purple")
    );

    public static final Item HALLOWEEN_CANDY_RED = register(
            "halloween_candy_red",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween_candy_red")
    );

    public static final Item HALLOWEEN_BLOOD_VIAL = register(
            "halloween_blood_vial",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.halloween_blood_vial")
    );

    public static final Item HALLOWEEN_PUMPKIN_COOKIE = register(
            "halloween_pumpkin_cookie",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween_pumpkin_cookie")
    );

    public static final Item HALLOWEEN_SKELETON_COOKIE = register(
            "halloween_skeleton_cookie",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween_skeleton_cookie")
    );

    public static final Item HALLOWEEN_ZOMBIE_COOKIE = register(
            "halloween_zombie_cookie",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween_zombie_cookie")
    );

    public static final Item HALLOWEEN_BUNDLE = register(
            "halloween_bundle",
            new HalloweenBundleItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))
    );

    // FUNKTIONALES Ticket (60 Minuten Session, Teleport + Adventure + Auto-Return)
    public static final Item HALLOWEEN_TICKET = register(
            "halloween_ticket",
            new TicketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    TicketManager.Target.HALLOWEEN, 3600L * 20L)
    );

    public static final Item HALLOWEEN25_BRONZE = register(
            "halloween25_bronze",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween25_bronze")
    );

    public static final Item HALLOWEEN25_SILVER = register(
            "halloween25_silver",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween25_silver")
    );

    public static final Item HALLOWEEN25_GOLD = register(
            "halloween25_gold",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween25_gold")
    );

    // ---- Safari / Voucher (Platzhalter) ----
    public static final Item SHINY_CHARM = register(
            "shiny_charm",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.shiny_charm")
    );

    // FUNKTIONALES Ticket (60 Minuten)
    public static final Item SAFARI_TICKET = register(
            "safari_ticket",
            new TicketItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    TicketManager.Target.SAFARI, 3600L * 20L)
    );

    public static final Item EVENT_VOUCHER_BLANK = register(
            "event_voucher_blank",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.voucher_blank")
    );

    public static final Item EVENT_VOUCHER_IV = register(
            "event_voucher_iv",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_iv")
    );

    public static final Item EVENT_VOUCHER_XP = register(
            "event_voucher_xp",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_xp")
    );

    public static final Item EVENT_VOUCHER_SHINY = register(
            "event_voucher_shiny",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.voucher_shiny")
    );

    public static final Item EVENT_VOUCHER_DROP = register(
            "event_voucher_drop",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_drop")
    );

    public static final Item EVENT_VOUCHER_EV = register(
            "event_voucher_ev",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.voucher_ev")
    );

    private static Item register(String path, Item item) {
        return Registry.register(
                BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, path),
                item
        );
    }

    public static void registerAll() {
        EvolutionBoost.LOGGER.info("[{}] Items registered.", EvolutionBoost.MOD_ID);
    }
}
