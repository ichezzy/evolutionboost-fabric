package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.coin_bronze")
    );

    public static final Item EVOLUTION_COIN_SILVER = register(
            "evolution_coin_silver",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.coin_silver")
    );

    public static final Item EVOLUTION_COIN_GOLD = register(
            "evolution_coin_gold",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.coin_gold")
    );

    public static final Item EVOLUTION_COIN_PLATINUM = register(
            "evolution_coin_platinum",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.evolution_coin_platinum")
    );

    // ---- Halloween Platzhalter ----
    public static final Item HALLOWEEN_CANDY = register(
            "halloween_candy",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween_pumpkin_cookie")
    );

    public static final Item HALLOWEEN_SKELETON_COOKIE = register(
            "halloween_skeleton_cookie",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.halloween_skeleton_cookie")
    );

    public static final Item HALLOWEEN_ZOMBIE_COOKIE = register(
            "halloween_zombie_cookie",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
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

    // CHRISTMAS ITEMS
    public static final Item CANDY_CANE = register(
            "candy_cane",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.candy_cane")
    );

    public static final Item CHRISTMAS_BALL_BLUE = register(
            "christmas_ball_blue",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.christmas_ball_blue")
    );

    public static final Item CHRISTMAS_BALL_GREEN = register(
            "christmas_ball_green",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.christmas_ball_green")
    );

    public static final Item CHRISTMAS_BALL_RED = register(
            "christmas_ball_red",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.christmas_ball_red")
    );

    public static final Item CHRISTMAS_BERRY = register(
            "christmas_berry",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.christmas_berry")
    );

    public static final Item CHRISTMAS_HAT = register(
            "christmas_hat",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.christmas_hat")
    );

    public static final Item CHRISTMAS_LOOT_SACK = register(
            "christmas_loot_sack",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.christmas_loot_sack")
    );

    public static final Item CHRISTMAS_SWEATER_BLUE = register(
            "christmas_sweater_blue",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.christmas_sweater_blue")
    );

    public static final Item CHRISTMAS_SWEATER_RED = register(
            "christmas_sweater_red",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.christmas_sweater_red")
    );

    public static final Item CHRISTMAS_TWIG = register(
            "christmas_twig",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.christmas_twig")
    );

    public static final Item CURSED_COAL = register(
            "cursed_coal",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.cursed_coal")
    );

    public static final Item CURSED_COAL_HEART = register(
            "cursed_coal_heart",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.cursed_coal_heart")
    );

    public static final Item CURSED_GIFT_BLACK = register(
            "cursed_gift_black",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.cursed_gift_black")
    );

    public static final Item CURSED_GIFT_PURPLE = register(
            "cursed_gift_purple",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.cursed_gift_purple")
    );

    public static final Item GIFT_BLUE = register(
            "gift_blue",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.gift_blue")
    );

    public static final Item GIFT_GREEN = register(
            "gift_green",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.gift_green")
    );

    public static final Item GIFT_RED = register(
            "gift_red",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.gift_red")
    );

    public static final Item GINGERBREAD = register(
            "gingerbread",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.gingerbread")
    );

    public static final Item HOLY_SPARK = register(
            "holy_spark",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.holy_spark")
    );

    public static final Item ICE_CROWN = register(
            "ice_crown",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.ice_crown")
    );

    public static final Item ICE_HEART = register(
            "ice_heart",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.ice_heart")
    );

    public static final Item ICE_QUEEN_RAID_KEY = register(
            "ice_queen_raid_key",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.ice_queen_raid_key")
    );

    public static final Item KRAMPUS_RAID_KEY = register(
            "krampus_raid_key",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.krampus_raid_key")
    );

    public static final Item LOST_TOY = register(
            "lost_toy",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.lost_toy")
    );

    public static final Item RED_NOSE = register(
            "red_nose",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.red_nose")
    );

    public static final Item SPIRIT_DEW = register(
            "spirit_dew",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.spirit_dew")
    );

    public static final Item SPIRIT_DEW_SHARDS = register(
            "spirit_dew_shards",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.spirit_dew_shards")
    );

    public static final Item WIND_UP_KEY = register(
            "wind_up_key",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.wind_up_key")
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.voucher_xp")
    );

    public static final Item EVENT_VOUCHER_SHINY = register(
            "event_voucher_shiny",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.voucher_shiny")
    );

    public static final Item EVENT_VOUCHER_DROP = register(
            "event_voucher_drop",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.voucher_drop")
    );

    public static final Item EVENT_VOUCHER_EV = register(
            "event_voucher_ev",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
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
