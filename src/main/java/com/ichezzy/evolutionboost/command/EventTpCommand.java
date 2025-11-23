package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import com.ichezzy.evolutionboost.ticket.TicketManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class EventTpCommand {
    private EventTpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("eventtp")
                .requires(src -> src.hasPermission(2))

                // /eventtp halloween
                .then(Commands.literal("halloween").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean ok = TicketManager.startManual(p, TicketManager.Target.HALLOWEEN);
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("[EventTP] Already in a session – use /eventtp return first."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Teleported to Halloween."), false);
                    return 1;
                }))

                // /eventtp safari
                .then(Commands.literal("safari").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean ok = TicketManager.startManual(p, TicketManager.Target.SAFARI);
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("[EventTP] Already in a session – use /eventtp return first."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Teleported to Safari Zone."), false);
                    return 1;
                }))

                // /eventtp christmas
                .then(Commands.literal("christmas").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    boolean ok = TicketManager.startManual(p, TicketManager.Target.CHRISTMAS);
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("[EventTP] Already in a session – use /eventtp return first."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Teleported to Christmas."), false);
                    return 1;
                }))

                // /eventtp return
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

                // /eventtp halloween setspawn
                .then(Commands.literal("halloween")
                        .then(Commands.literal("setspawn").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var pos = p.blockPosition();

                            EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
                            cfg.putSpawn("halloween", new EvolutionBoostConfig.Spawn(
                                    p.serverLevel().dimension().location().toString(),
                                    pos.getX(), pos.getY(), pos.getZ()
                            ));
                            EvolutionBoostConfig.save();

                            TicketManager.setSpawn(TicketManager.Target.HALLOWEEN, pos);

                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[EventTP] Halloween spawn set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
                            return 1;
                        }))
                )

                // /eventtp safari setspawn
                .then(Commands.literal("safari")
                        .then(Commands.literal("setspawn").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var pos = p.blockPosition();

                            EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
                            cfg.putSpawn("safari", new EvolutionBoostConfig.Spawn(
                                    p.serverLevel().dimension().location().toString(),
                                    pos.getX(), pos.getY(), pos.getZ()
                            ));
                            EvolutionBoostConfig.save();

                            TicketManager.setSpawn(TicketManager.Target.SAFARI, pos);

                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[EventTP] Safari spawn set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
                            return 1;
                        }))
                )

                // /eventtp christmas setspawn
                .then(Commands.literal("christmas")
                        .then(Commands.literal("setspawn").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var pos = p.blockPosition();

                            EvolutionBoostConfig cfg = EvolutionBoostConfig.get();
                            cfg.putSpawn("christmas", new EvolutionBoostConfig.Spawn(
                                    p.serverLevel().dimension().location().toString(),
                                    pos.getX(), pos.getY(), pos.getZ()
                            ));
                            EvolutionBoostConfig.save();

                            TicketManager.setSpawn(TicketManager.Target.CHRISTMAS, pos);

                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[EventTP] Christmas spawn set to " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), false);
                            return 1;
                        }))
                )
        );
    }
}
