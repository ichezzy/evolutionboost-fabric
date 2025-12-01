package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.boost.ActiveBoost;
import com.ichezzy.evolutionboost.boost.BoostColors;
import com.ichezzy.evolutionboost.boost.BoostManager;
import com.ichezzy.evolutionboost.boost.BoostScope;
import com.ichezzy.evolutionboost.boost.BoostType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Locale;

public final class BoostCommand {
    private BoostCommand() {}

    // ---- Suggestions ----

    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(BoostType.values()).map(Enum::name).toList(), b);

    private static final SuggestionProvider<CommandSourceStack> UNIT_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    new String[]{"s","m","h","d"}, b);

    /** Registrieren unter /evolutionboost boost … und /eb boost … */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var subtree = Commands.literal("boost")
                // falls du LuckPerms-Wrapper nutzen willst, ersetze diese Zeile:
                .requires(src -> src.hasPermission(2))

                // ================== ADD ==================
                .then(Commands.literal("add")
                        // ---- global ----
                        .then(Commands.literal("global")
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1D, 1000D))
                                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001D, 1_000_000D))
                                                        .then(Commands.argument("unit", StringArgumentType.word()).suggests(UNIT_SUGGEST)
                                                                .executes(ctx -> {
                                                                    var src = ctx.getSource();
                                                                    var server = src.getServer();
                                                                    BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                                    if (type == null) {
                                                                        src.sendFailure(Component.literal("Unknown boost type."));
                                                                        return 0;
                                                                    }
                                                                    double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                                    double value = DoubleArgumentType.getDouble(ctx, "value");
                                                                    String unit = StringArgumentType.getString(ctx, "unit");
                                                                    long durMs = DurationParser.fromValueUnit(value, unit);

                                                                    ActiveBoost ab = new ActiveBoost(type, BoostScope.GLOBAL, mult, durMs);
                                                                    BoostManager.get(server).addBoost(server, ab);

                                                                    // Admin-Nachricht
                                                                    src.sendSuccess(
                                                                            () -> Component.literal("[Boost] Added GLOBAL ")
                                                                                    .append(Component.literal(type.name())
                                                                                            .withStyle(BoostColors.chatColor(type), ChatFormatting.BOLD))
                                                                                    .append(Component.literal(" x" + mult + " for " + DurationParser.pretty(durMs))),
                                                                            false
                                                                    );

                                                                    // Broadcast an alle Spieler
                                                                    broadcastBoostStartGlobal(server, type, mult, durMs);

                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )

                        // ---- dimension ----
                        .then(Commands.literal("dim")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1D, 1000D))
                                                        .executes(ctx -> {
                                                            var src = ctx.getSource();
                                                            var server = src.getServer();
                                                            ResourceKey<Level> dim = DimensionArgument.getDimension(ctx, "dimension").dimension();
                                                            BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                            if (type == null) {
                                                                src.sendFailure(Component.literal("Unknown boost type."));
                                                                return 0;
                                                            }
                                                            double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                            BoostManager.get(server).setDimensionMultiplier(type, dim, mult);

                                                            src.sendSuccess(
                                                                    () -> Component.literal("[Boost] Set DIM ")
                                                                            .append(Component.literal(type.name())
                                                                                    .withStyle(BoostColors.chatColor(type), ChatFormatting.BOLD))
                                                                            .append(Component.literal(" x" + mult + " in " + dim.location())),
                                                                    false
                                                            );

                                                            // Broadcast an alle Spieler (mit Dimension-Hinweis)
                                                            broadcastBoostStartDim(server, type, mult, dim);

                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )

                // ================== CLEAR ==================
                .then(Commands.literal("clear")
                        // /eb boost clear all
                        .then(Commands.literal("all").executes(ctx -> {
                            var src = ctx.getSource();
                            var server = src.getServer();
                            int removed = BoostManager.get(server).clearAll();
                            src.sendSuccess(
                                    () -> Component.literal("[Boost] Cleared " + removed + " active global boosts."),
                                    false
                            );
                            return removed;
                        }))

                        // /eb boost clear global [type]
                        .then(Commands.literal("global")
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    var server = src.getServer();
                                    int removed = BoostManager.get(server).clearGlobal(null);
                                    src.sendSuccess(
                                            () -> Component.literal("[Boost] Cleared " + removed + " GLOBAL boosts."),
                                            false
                                    );
                                    return removed;
                                })
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            var server = src.getServer();
                                            BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                            if (type == null) {
                                                src.sendFailure(Component.literal("Unknown boost type."));
                                                return 0;
                                            }
                                            int removed = BoostManager.get(server).clearGlobal(type);
                                            src.sendSuccess(
                                                    () -> Component.literal("[Boost] Cleared " + removed + " GLOBAL boosts of type " + type.name() + "."),
                                                    false
                                            );
                                            return removed;
                                        })
                                )
                        )

                        // /eb boost clear dim <dimension> [type]
                        .then(Commands.literal("dim")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            var server = src.getServer();
                                            ResourceKey<Level> dim = DimensionArgument.getDimension(ctx, "dimension").dimension();
                                            BoostManager.get(server).clearAllDimensionMultipliers(dim);
                                            src.sendSuccess(
                                                    () -> Component.literal("[Boost] Cleared all DIM multipliers in " + dim.location() + "."),
                                                    false
                                            );
                                            return 1;
                                        })
                                        .then(Commands.literal("type")
                                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                                        .executes(ctx -> {
                                                            var src = ctx.getSource();
                                                            var server = src.getServer();
                                                            ResourceKey<Level> dim = DimensionArgument.getDimension(ctx, "dimension").dimension();
                                                            BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                            if (type == null) {
                                                                src.sendFailure(Component.literal("Unknown boost type."));
                                                                return 0;
                                                            }
                                                            BoostManager.get(server).clearDimensionMultiplier(type, dim);
                                                            src.sendSuccess(
                                                                    () -> Component.literal("[Boost] Cleared DIM multiplier for " + type.name() +
                                                                            " in " + dim.location() + "."),
                                                                    false
                                                            );
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                );

        // unter /evolutionboost & /eb anhängen
        d.register(Commands.literal("evolutionboost").then(subtree));
        d.register(Commands.literal("eb").then(subtree));
    }

    private static BoostType parseType(String raw) {
        if (raw == null) return null;
        String up = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return BoostType.valueOf(up);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --------- Broadcast-Helfer ---------

    private static void broadcastBoostStartGlobal(net.minecraft.server.MinecraftServer server,
                                                  BoostType type, double mult, long durMs) {
        if (server == null) return;

        ChatFormatting typeColor = BoostColors.chatColor(type);
        String duration = DurationParser.pretty(durMs);

        Component msg = Component.literal("[EVOLUTIONBOOST] ")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("GLOBAL ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(type.name())
                        .withStyle(typeColor, ChatFormatting.BOLD))
                .append(Component.literal(" x" + mult + " for " + duration)
                        .withStyle(ChatFormatting.WHITE));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    private static void broadcastBoostStartDim(net.minecraft.server.MinecraftServer server,
                                               BoostType type, double mult, ResourceKey<Level> dim) {
        if (server == null) return;

        ChatFormatting typeColor = BoostColors.chatColor(type);
        String dimName = dim.location().toString();

        Component msg = Component.literal("[EVOLUTIONBOOST] ")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("DIM ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(type.name())
                        .withStyle(typeColor, ChatFormatting.BOLD))
                .append(Component.literal(" x" + mult + " in " + dimName)
                        .withStyle(ChatFormatting.WHITE));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
