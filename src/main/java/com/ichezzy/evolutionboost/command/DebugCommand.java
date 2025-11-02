package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public final class DebugCommand {
    private DebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("ebdebug")
                .requires(src -> src.hasPermission(2))

                // /ebdebug dump
                .then(Commands.literal("dump")
                        .executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            server.levelKeys().forEach(key -> {
                                ServerLevel lvl = server.getLevel(key);
                                if (lvl == null) return;
                                var t = lvl.dimensionType();
                                String line = "[dump] " + key.location()
                                        + "  sky=" + t.hasSkyLight()
                                        + "  ceiling=" + t.hasCeiling()
                                        + "  ultraWarm=" + t.ultraWarm()
                                        + "  weatherCapable=" + (t.hasSkyLight() && !t.hasCeiling() && !t.ultraWarm())
                                        + "  daylightGamerule=" + lvl.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                                        + "  raining=" + lvl.isRaining() + " thunder=" + lvl.isThundering()
                                        + "  dayTime=" + lvl.getDayTime();
                                EvolutionBoost.LOGGER.info(line);
                                ctx.getSource().sendSuccess(() -> Component.literal(line), false);
                            });
                            return 1;
                        }))

                // /ebdebug dim <namespace:dim>
                .then(Commands.literal("dim")
                        .then(Commands.argument("dim", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    ServerLevel lvl = levelArg(ctx, "dim");
                                    if (lvl == null) return 0;
                                    var t = lvl.dimensionType();
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "[dim] " + lvl.dimension().location()
                                                    + "  sky=" + t.hasSkyLight()
                                                    + "  ceiling=" + t.hasCeiling()
                                                    + "  ultraWarm=" + t.ultraWarm()
                                                    + "  daylightGamerule=" + lvl.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)
                                                    + "  raining=" + lvl.isRaining() + " thunder=" + lvl.isThundering()
                                                    + "  dayTime=" + lvl.getDayTime()
                                    ), false);
                                    return 1;
                                })
                        )
                )

                // /ebdebug weather <dim> clear|rain|thunder [seconds]
                .then(Commands.literal("weather")
                        .then(Commands.argument("dim", ResourceLocationArgument.id())
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 36000))
                                                .executes(ctx -> {
                                                    ServerLevel lvl = levelArg(ctx, "dim");
                                                    if (lvl == null) return 0;
                                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    lvl.setWeatherParameters(sec * 20, 0, false, false);
                                                    msg(ctx.getSource(), "[weather] " + id(lvl) + " -> clear " + sec + "s");
                                                    return 1;
                                                }))
                                        .executes(ctx -> {
                                            ServerLevel lvl = levelArg(ctx, "dim");
                                            if (lvl == null) return 0;
                                            lvl.setWeatherParameters(20 * 60 * 5, 0, false, false);
                                            msg(ctx.getSource(), "[weather] " + id(lvl) + " -> clear");
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("rain")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 36000))
                                                .executes(ctx -> {
                                                    ServerLevel lvl = levelArg(ctx, "dim");
                                                    if (lvl == null) return 0;
                                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    lvl.setWeatherParameters(0, sec * 20, true, false);
                                                    msg(ctx.getSource(), "[weather] " + id(lvl) + " -> rain " + sec + "s");
                                                    return 1;
                                                }))
                                        .executes(ctx -> {
                                            ServerLevel lvl = levelArg(ctx, "dim");
                                            if (lvl == null) return 0;
                                            lvl.setWeatherParameters(0, 20 * 60 * 5, true, false);
                                            msg(ctx.getSource(), "[weather] " + id(lvl) + " -> rain");
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("thunder")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 36000))
                                                .executes(ctx -> {
                                                    ServerLevel lvl = levelArg(ctx, "dim");
                                                    if (lvl == null) return 0;
                                                    int sec = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    lvl.setWeatherParameters(0, sec * 20, true, true);
                                                    msg(ctx.getSource(), "[weather] " + id(lvl) + " -> thunder " + sec + "s");
                                                    return 1;
                                                }))
                                        .executes(ctx -> {
                                            ServerLevel lvl = levelArg(ctx, "dim");
                                            if (lvl == null) return 0;
                                            lvl.setWeatherParameters(0, 20 * 60 * 5, true, true);
                                            msg(ctx.getSource(), "[weather] " + id(lvl) + " -> thunder");
                                            return 1;
                                        })
                                )
                        )
                )

                // /ebdebug time <dim> <ticks 0..23999>
                .then(Commands.literal("time")
                        .then(Commands.argument("dim", ResourceLocationArgument.id())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0, 23999))
                                        .executes(ctx -> {
                                            ServerLevel lvl = levelArg(ctx, "dim");
                                            if (lvl == null) return 0;
                                            int t = IntegerArgumentType.getInteger(ctx, "ticks");
                                            long day = lvl.getDayTime() / 24000L;
                                            lvl.setDayTime(day * 24000L + t);
                                            msg(ctx.getSource(), "[time] " + id(lvl) + " -> set to " + t);
                                            return 1;
                                        })
                                )
                        )
                )

                // /ebdebug daylight <dim> on|off
                .then(Commands.literal("daylight")
                        .then(Commands.argument("dim", ResourceLocationArgument.id())
                                .then(Commands.literal("on").executes(ctx -> {
                                    ServerLevel lvl = levelArg(ctx, "dim");
                                    if (lvl == null) return 0;
                                    lvl.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, ctx.getSource().getServer());
                                    msg(ctx.getSource(), "[daylight] " + id(lvl) + " -> ON");
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ServerLevel lvl = levelArg(ctx, "dim");
                                    if (lvl == null) return 0;
                                    lvl.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, ctx.getSource().getServer());
                                    msg(ctx.getSource(), "[daylight] " + id(lvl) + " -> OFF");
                                    return 1;
                                }))
                        )
                )

                // /ebdebug tickonce <dim>
                .then(Commands.literal("tickonce")
                        .then(Commands.argument("dim", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    ServerLevel lvl = levelArg(ctx, "dim");
                                    if (lvl == null) return 0;
                                    lvl.setDayTime(lvl.getDayTime() + 20);
                                    msg(ctx.getSource(), "[tickonce] " + id(lvl) + " -> +20 ticks");
                                    return 1;
                                })
                        )
                )
        );
    }

    private static ServerLevel levelArg(CommandContext<CommandSourceStack> ctx, String name) {
        ResourceLocation rl = ResourceLocationArgument.getId(ctx, name);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        ServerLevel lvl = ctx.getSource().getServer().getLevel(key);
        if (lvl == null) {
            ctx.getSource().sendFailure(Component.literal("Dimension not loaded: " + rl));
        }
        return lvl;
    }

    private static void msg(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
        EvolutionBoost.LOGGER.info(text);
    }

    private static String id(ServerLevel lvl) {
        return lvl.dimension().location().toString();
    }
}
