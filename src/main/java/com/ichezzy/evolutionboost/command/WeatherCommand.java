package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.weather.EventWeatherManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class WeatherCommand {
    private WeatherCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {

        var subtree = Commands.literal("weather")

                // /eb weather christmas storm on|off
                .then(Commands.literal("christmas")
                        .then(Commands.literal("storm")
                                .requires(src -> EvolutionboostPermissions.check(
                                        src,
                                        "evolutionboost.weather.admin",
                                        2,
                                        false
                                ))
                                .then(Commands.literal("on").executes(ctx -> {
                                    EventWeatherManager.startChristmasStorm(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Weather] Christmas storm cycle STARTED in event:christmas.")
                                                    .withStyle(ChatFormatting.AQUA),
                                            false
                                    );
                                    return 1;
                                }))
                                .then(Commands.literal("off").executes(ctx -> {
                                    EventWeatherManager.stopChristmasStorm(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("[Weather] Christmas storm STOPPED and cleared in event:christmas.")
                                                    .withStyle(ChatFormatting.GRAY),
                                            false
                                    );
                                    return 1;
                                }))
                        )
                );

        // Unter /evolutionboost & /eb anhängen (wie bei Rewards/Boost/Event)
        d.register(Commands.literal("evolutionboost").then(subtree));
        d.register(Commands.literal("eb").then(subtree));
    }
}
