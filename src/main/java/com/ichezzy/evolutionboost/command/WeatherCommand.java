package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.configs.EventConfig;
import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.weather.ChristmasWeatherManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class WeatherCommand {
    private WeatherCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        var subtree = Commands.literal("weather")
                .requires(src -> EvolutionboostPermissions.check(
                        src, "evolutionboost.weather.admin", 2, false
                ))

                .then(Commands.literal("christmas")
                        // /eb weather christmas enable|disable - Master-Schalter
                        .then(Commands.literal("enable").executes(ctx -> {
                            EventConfig.setChristmasWeatherEnabled(true);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Weather] Christmas weather system ENABLED.")
                                            .withStyle(ChatFormatting.GREEN),
                                    false
                            );
                            return 1;
                        }))
                        .then(Commands.literal("disable").executes(ctx -> {
                            EventConfig.setChristmasWeatherEnabled(false);
                            ChristmasWeatherManager.stopChristmasStorm(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Weather] Christmas weather system DISABLED. No performance impact.")
                                            .withStyle(ChatFormatting.RED),
                                    false
                            );
                            return 1;
                        }))
                        
                        // /eb weather christmas storm on|off
                        .then(Commands.literal("storm")
                                .then(Commands.literal("on").executes(ctx -> {
                                    if (!EventConfig.isChristmasWeatherEnabled()) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("[Weather] Christmas weather is disabled! Use /eb weather christmas enable first.")
                                        );
                                        return 0;
                                    }
                                    ChristmasWeatherManager.startChristmasStorm(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Weather] Christmas storm STARTED.")
                                                    .withStyle(ChatFormatting.AQUA),
                                            false
                                    );
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ChristmasWeatherManager.stopChristmasStorm(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Weather] Christmas storm STOPPED.")
                                                    .withStyle(ChatFormatting.GRAY),
                                            false
                                    );
                                    return 1;
                                }))
                        )

                        // /eb weather christmas auto on|off
                        .then(Commands.literal("auto")
                                .then(Commands.literal("on").executes(ctx -> {
                                    if (!EventConfig.isChristmasWeatherEnabled()) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("[Weather] Christmas weather is disabled! Use /eb weather christmas enable first.")
                                        );
                                        return 0;
                                    }
                                    ChristmasWeatherManager.enableAutoStorm(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Weather] Auto storm cycle ENABLED.")
                                                    .withStyle(ChatFormatting.GREEN),
                                            false
                                    );
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    ChristmasWeatherManager.disableAutoStorm(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Weather] Auto storm cycle DISABLED.")
                                                    .withStyle(ChatFormatting.RED),
                                            false
                                    );
                                    return 1;
                                }))
                        )

                        // /eb weather christmas init - Setzt Basis-Boosts
                        .then(Commands.literal("init").executes(ctx -> {
                            if (!EventConfig.isChristmasWeatherEnabled()) {
                                ctx.getSource().sendFailure(
                                        Component.literal("[Weather] Christmas weather is disabled! Use /eb weather christmas enable first.")
                                );
                                return 0;
                            }
                            ChristmasWeatherManager.initializeChristmasBoosts(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Weather] Christmas boosts initialized.")
                                            .withStyle(ChatFormatting.GREEN),
                                    false
                            );
                            return 1;
                        }))

                        // /eb weather christmas status
                        .then(Commands.literal("status").executes(ctx -> {
                            String status = ChristmasWeatherManager.getStatus();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("[Weather] " + status)
                                            .withStyle(ChatFormatting.AQUA),
                                    false
                            );
                            return 1;
                        }))
                );

        d.register(Commands.literal("evolutionboost").then(subtree));
        d.register(Commands.literal("eb").then(subtree));
    }
}
