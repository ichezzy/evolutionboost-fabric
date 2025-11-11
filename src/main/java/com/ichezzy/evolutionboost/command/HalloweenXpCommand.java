package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.compat.cobblemon.XpHook;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class HalloweenXpCommand {
    private HalloweenXpCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("halloweenxp").requires(src -> src.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> {
                    XpHook.setHalloweenXpEnabled(true);
                    ctx.getSource().sendSuccess(() -> Component.literal("[EvolutionBoost] Halloween XP: ON (x2 in event:halloween)"), true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    XpHook.setHalloweenXpEnabled(false);
                    ctx.getSource().sendSuccess(() -> Component.literal("[EvolutionBoost] Halloween XP: OFF"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    boolean on = XpHook.isHalloweenXpEnabled();
                    ctx.getSource().sendSuccess(() -> Component.literal("[EvolutionBoost] Halloween XP status: " + (on ? "ON" : "OFF")), false);
                    return on ? 1 : 0;
                }))
                .then(Commands.literal("debug")
                        .then(Commands.literal("on").executes(ctx -> {
                            XpHook.setDebug(true);
                            ctx.getSource().sendSuccess(() -> Component.literal("[EvolutionBoost] Halloween XP DEBUG: ON"), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            XpHook.setDebug(false);
                            ctx.getSource().sendSuccess(() -> Component.literal("[EvolutionBoost] Halloween XP DEBUG: OFF"), true);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            boolean dbg = XpHook.isDebug();
                            ctx.getSource().sendSuccess(() -> Component.literal("[EvolutionBoost] Halloween XP DEBUG status: " + (dbg ? "ON" : "OFF")), false);
                            return dbg ? 1 : 0;
                        }))
                )
        );
    }
}
