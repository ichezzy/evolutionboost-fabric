package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.reward.RewardType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

public final class RewardCommand {
    private RewardCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(
                Commands.literal("rewards")

                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    RewardManager.sendInfo(ctx.getSource(), p);
                                    return 1;
                                })
                        )

                        .then(Commands.literal("claim")
                                .then(Commands.literal("daily")
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            return RewardManager.claim(p, RewardType.DAILY) ? 1 : 0;
                                        })
                                )
                                .then(Commands.literal("weekly")
                                        .executes(ctx -> {
                                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                                            return RewardManager.claim(p, RewardType.WEEKLY) ? 1 : 0;
                                        })
                                )
                                .then(Commands.literal("monthly")
                                        .then(Commands.literal("donator")
                                                .executes(ctx -> {
                                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                                    return RewardManager.claim(p, RewardType.MONTHLY_DONATOR) ? 1 : 0;
                                                })
                                        )
                                        .then(Commands.literal("gym")
                                                .executes(ctx -> {
                                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                                    return RewardManager.claim(p, RewardType.MONTHLY_GYM) ? 1 : 0;
                                                })
                                        )
                                )
                        )

                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("donator")
                                                .then(Commands.literal("true").executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    RewardManager.setMonthlyEligibility(target.getGameProfile().getName(), true, false);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR for ")
                                                            .append(target.getName()).append(Component.literal(": true").withStyle(ChatFormatting.GREEN)), false);
                                                    return 1;
                                                }))
                                                .then(Commands.literal("false").executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    RewardManager.setMonthlyEligibility(target.getGameProfile().getName(), false, false);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR for ")
                                                            .append(target.getName()).append(Component.literal(": false").withStyle(ChatFormatting.RED)), false);
                                                    return 1;
                                                }))
                                        )
                                        .then(Commands.literal("gym")
                                                .then(Commands.literal("true").executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    RewardManager.setMonthlyEligibility(target.getGameProfile().getName(), false, true);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set GYM for ")
                                                            .append(target.getName()).append(Component.literal(": true").withStyle(ChatFormatting.GREEN)), false);
                                                    return 1;
                                                }))
                                                .then(Commands.literal("false").executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    RewardManager.setMonthlyEligibility(target.getGameProfile().getName(), false, false);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set GYM for ")
                                                            .append(target.getName()).append(Component.literal(": false").withStyle(ChatFormatting.RED)), false);
                                                    return 1;
                                                }))
                                        )
                                )
                        )

                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("daily").executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            RewardManager.resetCooldown(target.getUUID(), RewardType.DAILY);
                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset DAILY for ").append(target.getName()), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("weekly").executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            RewardManager.resetCooldown(target.getUUID(), RewardType.WEEKLY);
                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset WEEKLY for ").append(target.getName()), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("monthly")
                                                .then(Commands.literal("donator").executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    RewardManager.resetCooldown(target.getUUID(), RewardType.MONTHLY_DONATOR);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset MONTHLY (DONATOR) for ").append(target.getName()), false);
                                                    return 1;
                                                }))
                                                .then(Commands.literal("gym").executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    RewardManager.resetCooldown(target.getUUID(), RewardType.MONTHLY_GYM);
                                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset MONTHLY (GYM) for ").append(target.getName()), false);
                                                    return 1;
                                                }))
                                        )
                                )
                        )
        );
    }
}
