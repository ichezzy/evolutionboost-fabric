package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.reward.RewardType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RewardCommand {
    private RewardCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rewards")
                .then(Commands.literal("help")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    """
                                    §e/rewards info§7 – show your reward status
                                    §e/rewards claim <daily|weekly|monthly_donator|monthly_gym>§7 – claim if available
                                    """), false);
                            return 1;
                        }))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            StringBuilder sb = new StringBuilder("Rewards: ");
                            for (RewardType t : RewardType.values()) {
                                long s = RewardManager.secondsUntilNext(p, t);
                                sb.append(t.name().toLowerCase()).append("=")
                                        .append(s == 0 ? "ready" : (s + "s"))
                                        .append("  ");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        }))
                .then(Commands.literal("claim")
                        .then(Commands.argument("type", StringArgumentType.string())
                                .suggests((c, b) -> {
                                    for (RewardType t : RewardType.values()) b.suggest(t.name().toLowerCase());
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    String s = StringArgumentType.getString(ctx, "type");
                                    RewardType type = RewardType.from(s);
                                    if (type == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown type: " + s));
                                        return 0;
                                    }
                                    return RewardManager.claim(p, type) ? 1 : 0;
                                })))
        );
    }
}
