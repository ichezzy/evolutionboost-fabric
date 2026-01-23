package com.ichezzy.evolutionboost.item;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
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
                    "tooltip.evolutionboost.coin_platinum")
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

    public static final Item CANDY_CANE_GREEN = register(
            "candy_cane_green",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.candy_cane_green")
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.christmas_berry")
    );

    public static final Item CHRISTMAS_CANDY = register(
            "christmas_candy",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.christmas_candy")
    );

    public static final Item CHRISTMAS_CANDY_BLUE = register(
            "christmas_candy_blue",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.christmas_candy_blue")
    );

    public static final Item CHRISTMAS_CANDY_PINK = register(
            "christmas_candy_pink",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.christmas_candy_pink")
    );

    public static final Item CHRISTMAS_CANDY_PURPLE = register(
            "christmas_candy_purple",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.christmas_candy_purple")
    );

    public static final Item CHRISTMAS_HAT = register(
            "christmas_hat",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.christmas_hat")
    );

    public static final Item CHRISTMAS_LOOT_SACK = register(
            "christmas_loot_sack",
            new ChristmasSackItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE))
    );

    public static final Item CHRISTMAS25_MEDAL = register(
            "christmas25_medal",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.christmas25_medal")
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.cursed_coal")
    );

    public static final Item CURSED_COAL_HEART = register(
            "cursed_coal_heart",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.cursed_coal_heart")
    );

    public static final Item CURSED_GIFT_BLACK = register(
            "cursed_gift_black",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.cursed_gift_black")
    );

    public static final Item CURSED_GIFT_PURPLE = register(
            "cursed_gift_purple",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON),
                    "tooltip.evolutionboost.cursed_gift_purple")
    );

    public static final Item FROZEN_YETI_TOY = register(
            "frozen_yeti_toy",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.frozen_yeti_toy")
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.gingerbread")
    );

    public static final Item GRINCH_HAT = register(
            "grinch_hat",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.COMMON),
                    "tooltip.evolutionboost.grinch_hat")
    );

    public static final Item HOLY_SPARK = register(
            "holy_spark",
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.COMMON),
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
            new SimpleTooltipItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.spirit_dew_shards")
    );

    public static final Item WIND_UP_KEY = register(
            "wind_up_key",
            new SimpleTooltipItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    "tooltip.evolutionboost.wind_up_key")
    );

    // ---- Charms (Permanent) ----
    public static final Item SHINY_CHARM = register(
            "shiny_charm",
            new ShinyCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final Item XP_CHARM = register(
            "xp_charm",
            new XPCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    // ---- Charms (30 Days) ----
    public static final Item SHINY_CHARM_30D = register(
            "shiny_charm_30d",
            new TimedShinyCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 30)
    );

    public static final Item XP_CHARM_30D = register(
            "xp_charm_30d",
            new TimedXPCharmItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 30)
    );

    // ---- Running Shoes (Permanent) ----
    public static final Item RUNNING_SHOES = register(
            "running_shoes",
            new RunningBootsItem(RunningBootsItem.Tier.NORMAL, 
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final Item GREAT_RUNNING_SHOES = register(
            "great_running_shoes",
            new RunningBootsItem(RunningBootsItem.Tier.GREAT,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item ULTRA_RUNNING_SHOES = register(
            "ultra_running_shoes",
            new RunningBootsItem(RunningBootsItem.Tier.ULTRA,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    // ---- Running Shoes (30 Days) ----
    public static final Item RUNNING_SHOES_30D = register(
            "running_shoes_30d",
            new TimedRunningBootsItem(RunningBootsItem.Tier.NORMAL,
                    new Item.Properties().stacksTo(1).rarity(Rarity.COMMON), 30)
    );

    public static final Item GREAT_RUNNING_SHOES_30D = register(
            "great_running_shoes_30d",
            new TimedRunningBootsItem(RunningBootsItem.Tier.GREAT,
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON), 30)
    );

    public static final Item ULTRA_RUNNING_SHOES_30D = register(
            "ultra_running_shoes_30d",
            new TimedRunningBootsItem(RunningBootsItem.Tier.ULTRA,
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE), 30)
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
            EventVoucherItem.forIV(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    );

    public static final Item EVENT_VOUCHER_XP = register(
            "event_voucher_xp",
            EventVoucherItem.forXP(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final Item EVENT_VOUCHER_SHINY = register(
            "event_voucher_shiny",
            EventVoucherItem.forShiny(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final Item EVENT_VOUCHER_EV = register(
            "event_voucher_ev",
            EventVoucherItem.forEV(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    // ---- Super EV Items ----
    public static final Item SUPER_HP_UP = register(
            "super_hp_up",
            SuperEVItem.forHP(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item SUPER_PROTEIN = register(
            "super_protein",
            SuperEVItem.forAttack(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item SUPER_IRON = register(
            "super_iron",
            SuperEVItem.forDefense(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item SUPER_CALCIUM = register(
            "super_calcium",
            SuperEVItem.forSpAttack(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item SUPER_ZINC = register(
            "super_zinc",
            SuperEVItem.forSpDefense(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item SUPER_CARBOS = register(
            "super_carbos",
            SuperEVItem.forSpeed(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    // Basis-Item für Shop Bundles
    public static final Item SUPER_MEDICINE = register(
            "super_medicine",
            new SimpleTooltipItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.super_medicine")
    );

    // EV Reset Item
    public static final Item RESET_MEDICINE = register(
            "reset_medicine",
            new EVResetItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))
    );

    // Universal Mint
    public static final Item UNIVERSAL_MINT = register(
            "universal_mint",
            new SimpleTooltipItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.universal_mint")
    );

    // Ability Voucher
    public static final Item ABILITY_VOUCHER = register(
            "ability_voucher",
            new SimpleTooltipItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    "tooltip.evolutionboost.ability_voucher")
    );

    // ---- Bottle Caps ----
    public static final Item BOTTLE_CAP_SILVER = register(
            "bottle_cap_silver",
            BottleCapItem.silver(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item BOTTLE_CAP_GOLD = register(
            "bottle_cap_gold",
            BottleCapItem.gold(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))
    );

    public static final Item BOTTLE_CAP_COPPER = register(
            "bottle_cap_copper",
            BottleCapItem.copper(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))
    );

    public static final Item BOTTLE_CAP_HP = register(
            "bottle_cap_hp",
            BottleCapItem.forStat(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    BottleCapItem.CapType.HP, "tooltip.evolutionboost.bottle_cap_hp")
    );

    public static final Item BOTTLE_CAP_ATK = register(
            "bottle_cap_atk",
            BottleCapItem.forStat(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    BottleCapItem.CapType.ATK, "tooltip.evolutionboost.bottle_cap_atk")
    );

    public static final Item BOTTLE_CAP_DEF = register(
            "bottle_cap_def",
            BottleCapItem.forStat(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    BottleCapItem.CapType.DEF, "tooltip.evolutionboost.bottle_cap_def")
    );

    public static final Item BOTTLE_CAP_SPATK = register(
            "bottle_cap_spatk",
            BottleCapItem.forStat(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    BottleCapItem.CapType.SPATK, "tooltip.evolutionboost.bottle_cap_spatk")
    );

    public static final Item BOTTLE_CAP_SPDEF = register(
            "bottle_cap_spdef",
            BottleCapItem.forStat(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    BottleCapItem.CapType.SPDEF, "tooltip.evolutionboost.bottle_cap_spdef")
    );

    public static final Item BOTTLE_CAP_SPEED = register(
            "bottle_cap_speed",
            BottleCapItem.forStat(new Item.Properties().stacksTo(16).rarity(Rarity.RARE),
                    BottleCapItem.CapType.SPEED, "tooltip.evolutionboost.bottle_cap_speed")
    );

    public static final Item BOTTLE_CAP_VOID = register(
            "bottle_cap_void",
            BottleCapItem.voidCap(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))
    );

    // ---- Swappers ----
    public static final Item SHINY_SWAPPER = register(
            "shiny_swapper",
            new ShinySwapperItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final Item GENDER_SWAPPER = register(
            "gender_swapper",
            new GenderSwapperItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    public static final Item CAUGHT_BALL_SWAPPER = register(
            "caught_ball_swapper",
            new CaughtBallSwapperItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE))
    );

    // ---- Blocks as Items ----
    public static final Item SPIRIT_ALTAR = register(
            "spirit_altar",
            new BlockItem(ModBlocks.SPIRIT_ALTAR, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
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
