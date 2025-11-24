package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.dimension.ChristmasEvent;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ChristmasCommand {
    private ChristmasCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("christmas")
                .requires(src -> src.hasPermission(2))

                // /christmas storm start
                .then(Commands.literal("storm").then(Commands.literal("start").executes(ctx -> {
                    boolean ok = ChristmasEvent.forceStart(ctx.getSource().getServer());
                    if (ok) ctx.getSource().sendSuccess(() -> Component.literal("[Christmas] Blizzard started."), false);
                    else    ctx.getSource().sendFailure(Component.literal("[Christmas] Already active or dimension missing."));
                    return ok ? 1 : 0;
                })))

                // /christmas storm stop
                .then(Commands.literal("storm").then(Commands.literal("stop").executes(ctx -> {
                    boolean ok = ChristmasEvent.forceStop(ctx.getSource().getServer());
                    if (ok) ctx.getSource().sendSuccess(() -> Component.literal("[Christmas] Blizzard stopped."), false);
                    else    ctx.getSource().sendFailure(Component.literal("[Christmas] No active blizzard."));
                    return ok ? 1 : 0;
                })))

                // /christmas storm status
                .then(Commands.literal("storm").then(Commands.literal("status").executes(ctx -> {
                    var s = ChristmasEvent.status(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(s), false);
                    return 1;
                })))
        );
    }
}
