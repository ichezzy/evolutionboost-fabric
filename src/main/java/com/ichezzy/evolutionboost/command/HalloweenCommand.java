package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.dimension.DimensionTimeHook;
import com.ichezzy.evolutionboost.dimension.HalloweenWeatherHook;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class HalloweenCommand {
    private HalloweenCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("halloween")
                .requires(src -> src.hasPermission(2))
                // ===== Sturm =====
                .then(Commands.literal("storm")
                        .then(Commands.literal("start")
                                .executes(ctx -> {
                                    HalloweenWeatherHook.startThunder(ctx.getSource().getServer(), 300);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Halloween storm started for 300s."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("short")
                                .executes(ctx -> {
                                    HalloweenWeatherHook.startThunder(ctx.getSource().getServer(), 30);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Halloween storm started for 30s (test)."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("stop")
                                .executes(ctx -> {
                                    HalloweenWeatherHook.clearThunder(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(() -> Component.literal("Halloween storm stopped."), false);
                                    return 1;
                                }))
                )
                // ===== TimeFreeze =====
                .then(Commands.literal("timefreeze")
                        .then(Commands.literal("on")
                                .executes(ctx -> {
                                    DimensionTimeHook.setEnabled(true);
                                    ctx.getSource().sendSuccess(() -> Component.literal("TimeFreeze enabled (Halloween dimension)."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("off")
                                .executes(ctx -> {
                                    DimensionTimeHook.setEnabled(false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("TimeFreeze disabled."), false);
                                    return 1;
                                }))
                        .then(Commands.literal("set")
                                .then(Commands.literal("midnight")
                                        .executes(ctx -> {
                                            DimensionTimeHook.setMidnight();
                                            ctx.getSource().sendSuccess(() -> Component.literal("TimeFreeze target set to midnight (18000)."), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("noon")
                                        .executes(ctx -> {
                                            DimensionTimeHook.setNoon();
                                            ctx.getSource().sendSuccess(() -> Component.literal("TimeFreeze target set to noon (6000)."), false);
                                            return 1;
                                        }))
                                .then(Commands.argument("ticks", LongArgumentType.longArg(0, 23999))
                                        .executes(ctx -> {
                                            long t = LongArgumentType.getLong(ctx, "ticks");
                                            DimensionTimeHook.setTicks(t);
                                            ctx.getSource().sendSuccess(() -> Component.literal("TimeFreeze target set to " + t + " ticks."), false);
                                            return 1;
                                        }))
                        )
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    boolean on = DimensionTimeHook.isEnabled();
                                    long t = DimensionTimeHook.target();
                                    ctx.getSource().sendSuccess(() -> Component.literal("TimeFreeze: " + (on ? "ON" : "OFF") + ", target=" + t), false);
                                    return 1;
                                }))
                )
        );
    }
}
