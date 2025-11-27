package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.boost.ActiveBoost;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostScope;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class BoostCommand {
    private BoostCommand() {}

    // ---- Suggestions ----
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_TYPES = (ctx, b) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(BoostType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)),
                    b
            );

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_UNITS = (ctx, b) ->
            SharedSuggestionProvider.suggest(
                    Arrays.asList("s", "sec", "m", "min", "h", "hour", "d", "day"),
                    b
            );

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        // WICHTIG: OP-Gate direkt am "boost"-Literal,
        // NICHT an "evolutionboost"/"eb", damit /eb rewards weiter für alle geht.
        var boostRoot = Commands.literal("boost")
                .requires(src -> src.hasPermission(2))  // <- nur OP (Permission-Level 2)

                // ==================== /evolutionboost boost add ... ====================
                .then(Commands.literal("add")

                        // /evolutionboost boost add global <type> <mult> <value> <unit>
                        .then(Commands.literal("global")
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(SUGGEST_TYPES)
                                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0))
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001))
                                                        .then(Commands.argument("unit", StringArgumentType.word()).suggests(SUGGEST_UNITS)
                                                                .executes(ctx -> {
                                                                    var src = ctx.getSource();
                                                                    BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                                    if (type == null) {
                                                                        src.sendFailure(Component.literal("[Boost] Unknown type."));
                                                                        return 0;
                                                                    }
                                                                    double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                                    double val = DoubleArgumentType.getDouble(ctx, "value");
                                                                    String unit = StringArgumentType.getString(ctx, "unit");
                                                                    long durMs = DurationParser.fromValueUnit(val, unit);

                                                                    ActiveBoost ab = new ActiveBoost(
                                                                            type,
                                                                            BoostScope.GLOBAL,
                                                                            Math.max(0.0, mult),
                                                                            durMs,
                                                                            null,
                                                                            null
                                                                    );
                                                                    String key = BoostManager.get(src.getServer()).addBoost(src.getServer(), ab);
                                                                    src.sendSuccess(
                                                                            () -> Component.literal("[Boost] Added GLOBAL " + type + " x" + mult +
                                                                                    " for " + DurationParser.pretty(durMs) + " (key=" + key + ")"),
                                                                            false
                                                                    );
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )

                        // /evolutionboost boost add player <player> <type> <mult> <value> <unit>
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SUGGEST_TYPES)
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0))
                                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001))
                                                                .then(Commands.argument("unit", StringArgumentType.word()).suggests(SUGGEST_UNITS)
                                                                        .executes(ctx -> {
                                                                            var src = ctx.getSource();
                                                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                                            BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                                            if (type == null) {
                                                                                src.sendFailure(Component.literal("[Boost] Unknown type."));
                                                                                return 0;
                                                                            }
                                                                            double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                                            double val = DoubleArgumentType.getDouble(ctx, "value");
                                                                            String unit = StringArgumentType.getString(ctx, "unit");
                                                                            long durMs = DurationParser.fromValueUnit(val, unit);

                                                                            UUID pid = target.getUUID();
                                                                            String pname = target.getGameProfile().getName();
                                                                            ActiveBoost ab = new ActiveBoost(
                                                                                    type,
                                                                                    BoostScope.PLAYER,
                                                                                    Math.max(0.0, mult),
                                                                                    durMs,
                                                                                    pid,
                                                                                    pname
                                                                            );
                                                                            String key = BoostManager.get(src.getServer()).addBoost(src.getServer(), ab);
                                                                            src.sendSuccess(
                                                                                    () -> Component.literal("[Boost] Added PLAYER " + pname + " " + type +
                                                                                            " x" + mult + " for " + DurationParser.pretty(durMs) +
                                                                                            " (key=" + key + ")"),
                                                                                    false
                                                                            );
                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )

                        // /evolutionboost boost add dim <dimension> <type> <mult>
                        .then(Commands.literal("dim")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SUGGEST_TYPES)
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0))
                                                        .executes(ctx -> {
                                                            var src = ctx.getSource();
                                                            ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
                                                            ResourceKey<Level> dimKey = level.dimension();
                                                            BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                            if (type == null) {
                                                                src.sendFailure(Component.literal("[Boost] Unknown type."));
                                                                return 0;
                                                            }
                                                            double mult = DoubleArgumentType.getDouble(ctx, "multiplier");

                                                            BoostManager.get(src.getServer())
                                                                    .setDimensionMultiplier(type, dimKey, Math.max(0.0, mult));
                                                            src.sendSuccess(
                                                                    () -> Component.literal("[Boost] Dimension " + dimKey.location() + " " + type +
                                                                            " multiplier = x" + mult),
                                                                    false
                                                            );
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )

                // ==================== /evolutionboost boost clear ... ====================
                .then(Commands.literal("clear")

                        // /evolutionboost boost clear global [type]
                        .then(Commands.literal("global")
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    int n = BoostManager.get(src.getServer()).clearGlobal(null);
                                    src.sendSuccess(
                                            () -> Component.literal("[Boost] Cleared " + n + " GLOBAL boosts"),
                                            false
                                    );
                                    return 1;
                                })
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(SUGGEST_TYPES)
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                            if (type == null) {
                                                src.sendFailure(Component.literal("[Boost] Unknown type."));
                                                return 0;
                                            }
                                            int n = BoostManager.get(src.getServer()).clearGlobal(type);
                                            src.sendSuccess(
                                                    () -> Component.literal("[Boost] Cleared " + n + " GLOBAL boosts of " + type),
                                                    false
                                            );
                                            return 1;
                                        })
                                )
                        )

                        // /evolutionboost boost clear player <player> [type]
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            int n = BoostManager.get(src.getServer()).clearPlayer(target.getUUID(), null);
                                            src.sendSuccess(
                                                    () -> Component.literal("[Boost] Cleared " + n + " PLAYER boosts for "
                                                            + target.getName().getString()),
                                                    false
                                            );
                                            return 1;
                                        })
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SUGGEST_TYPES)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                    if (type == null) {
                                                        src.sendFailure(Component.literal("[Boost] Unknown type."));
                                                        return 0;
                                                    }
                                                    int n = BoostManager.get(src.getServer())
                                                            .clearPlayer(target.getUUID(), type);
                                                    src.sendSuccess(
                                                            () -> Component.literal("[Boost] Cleared " + n + " PLAYER boosts of " + type +
                                                                    " for " + target.getName().getString()),
                                                            false
                                                    );
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /evolutionboost boost clear dim <dimension> [type]
                        .then(Commands.literal("dim")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
                                            ResourceKey<Level> dimKey = level.dimension();
                                            BoostManager.get(src.getServer()).clearAllDimensionMultipliers(dimKey);
                                            src.sendSuccess(
                                                    () -> Component.literal("[Boost] Cleared ALL dimension multipliers for "
                                                            + dimKey.location()),
                                                    false
                                            );
                                            return 1;
                                        })
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(SUGGEST_TYPES)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");
                                                    ResourceKey<Level> dimKey = level.dimension();
                                                    BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                    if (type == null) {
                                                        src.sendFailure(Component.literal("[Boost] Unknown type."));
                                                        return 0;
                                                    }
                                                    BoostManager.get(src.getServer()).clearDimensionMultiplier(type, dimKey);
                                                    src.sendSuccess(
                                                            () -> Component.literal("[Boost] Cleared " + type +
                                                                    " multiplier for " + dimKey.location()),
                                                            false
                                                    );
                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /evolutionboost boost clear all
                        .then(Commands.literal("all")
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    int n = BoostManager.get(src.getServer()).clearAll();
                                    src.sendSuccess(
                                            () -> Component.literal("[Boost] Cleared ALL boosts (" + n + ")"),
                                            false
                                    );
                                    return 1;
                                })
                        )
                );

        // Unter /evolutionboost und /eb anhängen – OHNE requires hier,
        // damit /evolutionboost rewards … weiterhin allen offen steht.
        d.register(Commands.literal("evolutionboost").then(boostRoot));
        d.register(Commands.literal("eb").then(boostRoot));
    }

    private static BoostType parseType(String s) {
        if (s == null) return null;
        String k = s.trim().toUpperCase(Locale.ROOT);
        for (BoostType t : BoostType.values()) {
            if (t.name().equals(k)) return t;
        }
        return null;
    }
}
