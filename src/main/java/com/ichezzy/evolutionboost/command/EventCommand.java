package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.item.TicketManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class EventCommand {
    private EventCommand(){}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var subtree = Commands.literal("event")
                // Nur OP oder Spieler mit evolutionboost.event
                .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.event", 2, false))

                .then(Commands.literal("tp")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ServerLevel dst = DimensionArgument.getDimension(ctx, "dimension");
                                    TicketManager.Target tgt = TicketManager.Target.from(dst.dimension().location().getPath());
                                    if (tgt == null) {
                                        // erlaub nur event:* Ziele
                                        if (!dst.dimension().location().getNamespace().equals("event")) {
                                            ctx.getSource().sendFailure(Component.literal("[EventTP] Only 'event:*' dimensions are allowed."));
                                            return 0;
                                        }
                                        // Manuell: kein Timer, kein Mode-Wechsel
                                        var pos = TicketManager.getSpawn(TicketManager.Target.SAFARI);
                                        p.teleportTo(dst, pos.getX() + .5, pos.getY(), pos.getZ() + .5, p.getYRot(), p.getXRot());
                                        ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Teleported."), false);
                                        return 1;
                                    }
                                    boolean ok = TicketManager.startManual(p, tgt);
                                    if (!ok) {
                                        ctx.getSource().sendFailure(Component.literal("[EventTP] Already in a session – use /evolutionboost event return first."));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Teleported to " + tgt.key() + "."), false);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("return").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean ok = TicketManager.returnNow(p);
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("[EventTP] No active manual session."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Returned."), false);
                    return 1;
                }))
                // setspawn: /eb event setspawn <halloween|safari|christmas>
                .then(Commands.literal("setspawn")
                        .then(Commands.literal("halloween").executes(ctx -> setSpawn(ctx.getSource(), TicketManager.Target.HALLOWEEN)))
                        .then(Commands.literal("safari").executes(ctx -> setSpawn(ctx.getSource(), TicketManager.Target.SAFARI)))
                        .then(Commands.literal("christmas").executes(ctx -> setSpawn(ctx.getSource(), TicketManager.Target.CHRISTMAS)))
                );

        // unter /evolutionboost und /eb aufhängen
        d.register(Commands.literal("evolutionboost").then(subtree));
        d.register(Commands.literal("eb").then(subtree));
    }

    private static int setSpawn(CommandSourceStack src, TicketManager.Target t) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var pos = p.blockPosition();
        TicketManager.setSpawn(t, pos);
        src.sendSuccess(() -> Component.literal("[EventTP] " + t.key() + " spawn set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
        return 1;
    }
}
