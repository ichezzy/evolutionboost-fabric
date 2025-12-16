package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.configs.EvolutionBoostConfig;
import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.item.TicketManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class EventCommand {
    private EventCommand(){}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var subtree = Commands.literal("event")
                // Nur OP oder Spieler mit evolutionboost.event
                .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.event", 2, false))

                // /eb event tp <dimension> - Einfacher Teleport zum Event-Spawn (keine Session)
                .then(Commands.literal("tp")
                        .then(Commands.argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ServerLevel dst = DimensionArgument.getDimension(ctx, "dimension");

                                    // Versuche Target aus der Dimension zu ermitteln
                                    TicketManager.Target tgt = TicketManager.Target.from(dst.dimension().location().getPath());

                                    BlockPos spawnPos;
                                    if (tgt != null) {
                                        // Bekanntes Event-Ziel -> nutze konfigurierten Spawn
                                        spawnPos = TicketManager.getSpawn(tgt);
                                    } else {
                                        // Unbekannte Dimension -> prüfe ob event:* Namespace
                                        if (!dst.dimension().location().getNamespace().equals("event")) {
                                            ctx.getSource().sendFailure(Component.literal("[EventTP] Only 'event:*' dimensions are allowed."));
                                            return 0;
                                        }
                                        // Fallback: versuche Spawn aus Config zu laden oder nutze Default
                                        String dimPath = dst.dimension().location().getPath();
                                        EvolutionBoostConfig.Spawn configSpawn = EvolutionBoostConfig.get().getSpawn(dimPath);
                                        if (configSpawn != null) {
                                            spawnPos = configSpawn.toBlockPos();
                                        } else {
                                            // Default Spawn für unbekannte Event-Dimensionen
                                            spawnPos = new BlockPos(0, 80, 0);
                                        }
                                    }

                                    // Einfacher Teleport - KEINE Session, KEIN Speichern der Rückkehr-Position
                                    p.teleportTo(dst, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, p.getYRot(), p.getXRot());

                                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Teleported to " + dst.dimension().location() + "."), false);
                                    return 1;
                                })
                        )
                )

                // /eb event return - Teleportiert zum Overworld-Spawn (nicht zur gespeicherten Position)
                .then(Commands.literal("return").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerLevel overworld = p.server.getLevel(Level.OVERWORLD);

                    if (overworld == null) {
                        ctx.getSource().sendFailure(Component.literal("[EventTP] Could not find Overworld."));
                        return 0;
                    }

                    // Teleport zum Overworld-Spawnpunkt
                    BlockPos spawnPos = overworld.getSharedSpawnPos();
                    p.teleportTo(overworld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, p.getYRot(), p.getXRot());

                    ctx.getSource().sendSuccess(() -> Component.literal("[EventTP] Returned to Overworld spawn."), false);
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