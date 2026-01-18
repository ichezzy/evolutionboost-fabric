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

    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(BoostType.values()).map(Enum::name).toList(), b);

    private static final SuggestionProvider<CommandSourceStack> UNIT_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    new String[]{"s","m","h","d"}, b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var subtree = Commands.literal("boost")
                .requires(src -> src.hasPermission(2))

                // ================== ADD ==================
                .then(Commands.literal("add")
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
                                                                        src.sendFailure(Component.literal("✗ Unknown boost type.")
                                                                                .withStyle(ChatFormatting.RED));
                                                                        return 0;
                                                                    }
                                                                    double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                                    double value = DoubleArgumentType.getDouble(ctx, "value");
                                                                    String unit = StringArgumentType.getString(ctx, "unit");
                                                                    long durMs = DurationParser.fromValueUnit(value, unit);

                                                                    ActiveBoost ab = new ActiveBoost(type, BoostScope.GLOBAL, mult, durMs);
                                                                    BoostManager.get(server).addBoost(server, ab);

                                                                    broadcastBoostStart(server, type, mult, durMs, null);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )

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
                                                                src.sendFailure(Component.literal("✗ Unknown boost type.")
                                                                        .withStyle(ChatFormatting.RED));
                                                                return 0;
                                                            }
                                                            double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                                                            BoostManager.get(server).setDimensionMultiplier(type, dim, mult);

                                                            broadcastBoostStart(server, type, mult, 0, dim);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )

                // ================== CLEAR ==================
                .then(Commands.literal("clear")
                        .then(Commands.literal("all").executes(ctx -> {
                            var src = ctx.getSource();
                            var server = src.getServer();
                            int removed = BoostManager.get(server).clearAll();
                            
                            Component msg = Component.literal("[EvolutionBoost] ")
                                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                                    .append(Component.literal("All boosts cleared ")
                                            .withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal("(" + removed + " removed)")
                                            .withStyle(ChatFormatting.WHITE));
                            
                            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                p.sendSystemMessage(msg);
                            }
                            return removed;
                        }))

                        .then(Commands.literal("global")
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    var server = src.getServer();
                                    int removed = BoostManager.get(server).clearGlobal(null);
                                    src.sendSuccess(() -> Component.literal("✓ Cleared " + removed + " global boosts")
                                            .withStyle(ChatFormatting.GREEN), false);
                                    return removed;
                                })
                                .then(Commands.literal("type")
                                        .then(Commands.argument("type", StringArgumentType.word()).suggests(TYPE_SUGGEST)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    var server = src.getServer();
                                                    BoostType type = parseType(StringArgumentType.getString(ctx, "type"));
                                                    if (type == null) {
                                                        src.sendFailure(Component.literal("✗ Unknown boost type.")
                                                                .withStyle(ChatFormatting.RED));
                                                        return 0;
                                                    }
                                                    int removed = BoostManager.get(server).clearGlobal(type);
                                                    src.sendSuccess(() -> Component.literal("✓ Cleared " + removed + " global ")
                                                            .withStyle(ChatFormatting.GREEN)
                                                            .append(Component.literal(type.name())
                                                                    .withStyle(BoostColors.chatColor(type), ChatFormatting.BOLD))
                                                            .append(Component.literal(" boosts")
                                                                    .withStyle(ChatFormatting.GREEN)), false);
                                                    return removed;
                                                })
                                        )
                                )
                        )

                        .then(Commands.literal("dim")
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            var server = src.getServer();
                                            ResourceKey<Level> dim = DimensionArgument.getDimension(ctx, "dimension").dimension();
                                            BoostManager.get(server).clearAllDimensionMultipliers(dim);
                                            src.sendSuccess(() -> Component.literal("✓ Cleared all multipliers in ")
                                                    .withStyle(ChatFormatting.GREEN)
                                                    .append(Component.literal(dim.location().toString())
                                                            .withStyle(ChatFormatting.AQUA)), false);
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
                                                                src.sendFailure(Component.literal("✗ Unknown boost type.")
                                                                        .withStyle(ChatFormatting.RED));
                                                                return 0;
                                                            }
                                                            BoostManager.get(server).clearDimensionMultiplier(type, dim);
                                                            src.sendSuccess(() -> Component.literal("✓ Cleared ")
                                                                    .withStyle(ChatFormatting.GREEN)
                                                                    .append(Component.literal(type.name())
                                                                            .withStyle(BoostColors.chatColor(type), ChatFormatting.BOLD))
                                                                    .append(Component.literal(" in ")
                                                                            .withStyle(ChatFormatting.GREEN))
                                                                    .append(Component.literal(dim.location().toString())
                                                                            .withStyle(ChatFormatting.AQUA)), false);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                );

        d.register(Commands.literal("evolutionboost").then(subtree));
        d.register(Commands.literal("eb").then(subtree));

        // /eb boost info - für alle Spieler
        var infoCmd = Commands.literal("boost")
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            showBoostInfo(ctx.getSource());
                            return 1;
                        }));
        
        d.register(Commands.literal("evolutionboost").then(infoCmd));
        d.register(Commands.literal("eb").then(infoCmd));
    }

    private static void showBoostInfo(CommandSourceStack src) {
        BoostManager manager = BoostManager.get(src.getServer());
        
        src.sendSystemMessage(Component.literal("══════ Active Boosts ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        boolean anyActive = false;

        for (BoostType type : BoostType.values()) {
            double mult = manager.getMultiplierFor(type, null);
            if (mult > 1.0) {
                anyActive = true;
                ChatFormatting color = BoostColors.chatColor(type);
                String icon = getIcon(type);
                src.sendSystemMessage(Component.literal("  " + icon + " ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(type.name())
                                .withStyle(color, ChatFormatting.BOLD))
                        .append(Component.literal(" x" + mult)
                                .withStyle(ChatFormatting.WHITE)));
            }
        }

        if (!anyActive) {
            src.sendSystemMessage(Component.literal("  No active boosts")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        src.sendSystemMessage(Component.literal("════════════════════════════")
                .withStyle(ChatFormatting.GOLD));
    }

    private static String getIcon(BoostType type) {
        return switch (type) {
            case IV -> "💎";
            case XP -> "⭐";
            case SHINY -> "✨";
            case EV -> "📈";
        };
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

    private static void broadcastBoostStart(net.minecraft.server.MinecraftServer server,
                                            BoostType type, double mult, long durMs, ResourceKey<Level> dim) {
        if (server == null) return;

        ChatFormatting typeColor = BoostColors.chatColor(type);
        String icon = getIcon(type);

        Component msg;
        if (dim == null) {
            // Global boost
            String duration = DurationParser.pretty(durMs);
            msg = Component.literal("[EvolutionBoost] ")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal(icon + " " + type.name() + " x" + mult)
                            .withStyle(typeColor, ChatFormatting.BOLD))
                    .append(Component.literal(" activated for " + duration)
                            .withStyle(ChatFormatting.GRAY));
        } else {
            // Dimension boost
            msg = Component.literal("[EvolutionBoost] ")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal(icon + " " + type.name() + " x" + mult)
                            .withStyle(typeColor, ChatFormatting.BOLD))
                    .append(Component.literal(" set in ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(dim.location().toString())
                            .withStyle(ChatFormatting.AQUA));
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
}
