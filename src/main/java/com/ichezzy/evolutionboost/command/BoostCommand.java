package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.boost.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class BoostCommand {
    private BoostCommand() {}

    private static final List<String> TYPES = Arrays.asList("shiny","xp","drop","iv");
    private static final List<String> UNITS = Arrays.asList("s","m","h","d"); // seconds, minutes, hours, days

    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGEST =
            (ctx, b) -> suggest(b, TYPES);
    private static final SuggestionProvider<CommandSourceStack> UNIT_SUGGEST =
            (ctx, b) -> suggest(b, UNITS);

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggest(SuggestionsBuilder b, List<String> vals) {
        String rem = b.getRemaining().toLowerCase();
        for (String v : vals) if (v.toLowerCase().startsWith(rem)) b.suggest(v);
        return b.buildFuture();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("boost").requires(src -> src.hasPermission(2))
                // /boost global <type> <multiplier> <duration> <unit>
                .then(Commands.literal("global")
                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(1.0))
                                        .then(Commands.argument("duration", DoubleArgumentType.doubleArg(0.001))
                                                .then(Commands.argument("unit", StringArgumentType.word()).suggests(UNIT_SUGGEST)
                                                        .executes(ctx -> {
                                                            var server = ctx.getSource().getServer();
                                                            var type = BoostType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
                                                            double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                            double durVal = DoubleArgumentType.getDouble(ctx, "duration");
                                                            String unit = StringArgumentType.getString(ctx, "unit");
                                                            long durMs = DurationParser.fromValueUnit(durVal, unit);
                                                            var ab = new ActiveBoost(type, BoostScope.GLOBAL, mult, durMs, null);
                                                            BoostManager.get(server).addBoost(server, ab);
                                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                                    "Global " + type + " x" + mult + " for " + DurationParser.pretty(durMs)), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )
                // /boost player <player> <type> <multiplier> <duration> <unit>
                .then(Commands.literal("player")
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                        .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(1.0))
                                                .then(Commands.argument("duration", DoubleArgumentType.doubleArg(0.001))
                                                        .then(Commands.argument("unit", StringArgumentType.word()).suggests(UNIT_SUGGEST)
                                                                .executes(ctx -> {
                                                                    var server = ctx.getSource().getServer();
                                                                    ServerPlayer sp = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                                                    var type = BoostType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
                                                                    double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                                    double durVal = DoubleArgumentType.getDouble(ctx, "duration");
                                                                    String unit = StringArgumentType.getString(ctx, "unit");
                                                                    long durMs = DurationParser.fromValueUnit(durVal, unit);
                                                                    var ab = new ActiveBoost(type, BoostScope.PLAYER, mult, durMs, sp.getUUID(), sp.getGameProfile().getName());
                                                                    BoostManager.get(server).addBoost(server, ab);
                                                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                                                            "Player " + sp.getGameProfile().getName() + ": " + type + " x" + mult
                                                                                    + " for " + DurationParser.pretty(durMs)), true);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                )
                // clear bleibt wie gehabt (+ all-Optionen, die du bereits eingebaut hast)
                .then(Commands.literal("clear")
                        .then(Commands.literal("all").executes(ctx -> {
                            int n = BoostManager.get(ctx.getSource().getServer()).clearAll();
                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " boosts (global + player)"), true);
                            return n;
                        }))
                        .then(Commands.literal("global")
                                .executes(ctx -> {
                                    int n = BoostManager.get(ctx.getSource().getServer()).clearGlobal(null);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " global boosts"), true);
                                    return n;
                                })
                                .then(Commands.literal("all").executes(ctx -> {
                                    int n = BoostManager.get(ctx.getSource().getServer()).clearGlobal(null);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " global boosts"), true);
                                    return n;
                                }))
                                .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST).executes(ctx -> {
                                    var type = BoostType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
                                    int n = BoostManager.get(ctx.getSource().getServer()).clearGlobal(type);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " global " + type + " boosts"), true);
                                    return n;
                                }))
                        )
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer sp = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            int n = BoostManager.get(ctx.getSource().getServer()).clearPlayer(sp.getUUID(), null);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " boosts for " + sp.getGameProfile().getName()), true);
                                            return n;
                                        })
                                        .then(Commands.literal("all").executes(ctx -> {
                                            ServerPlayer sp = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            int n = BoostManager.get(ctx.getSource().getServer()).clearPlayer(sp.getUUID(), null);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " boosts for " + sp.getGameProfile().getName()), true);
                                            return n;
                                        }))
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST).executes(ctx -> {
                                            ServerPlayer sp = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            var type = BoostType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
                                            int n = BoostManager.get(ctx.getSource().getServer()).clearPlayer(sp.getUUID(), type);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Cleared " + n + " " + type + " boosts for " + sp.getGameProfile().getName()), true);
                                            return n;
                                        }))
                                )
                        )
                )
        );
    }
}
