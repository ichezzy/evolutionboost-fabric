package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.configs.RewardConfig;
import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.reward.RewardManager;
import com.ichezzy.evolutionboost.reward.RewardManager.DonatorTier;
import com.ichezzy.evolutionboost.reward.RewardType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RewardCommand {
    private RewardCommand(){}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var subtree = Commands.literal("rewards")

                // ---- Spieler-Kommandos: immer erlaubt ----
                .then(Commands.literal("info").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    RewardManager.sendInfo(ctx.getSource(), p);
                    return 1;
                }))

                .then(Commands.literal("claim")
                        .then(Commands.literal("daily").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return RewardManager.claim(p, RewardType.DAILY) ? 1 : 0;
                        }))
                        .then(Commands.literal("weekly").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            return RewardManager.claim(p, RewardType.WEEKLY) ? 1 : 0;
                        }))
                        .then(Commands.literal("monthly")
                                .then(Commands.literal("donator").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    return RewardManager.claim(p, RewardType.MONTHLY_DONATOR) ? 1 : 0;
                                }))
                                .then(Commands.literal("gym").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    return RewardManager.claim(p, RewardType.MONTHLY_GYM) ? 1 : 0;
                                }))
                                .then(Commands.literal("staff").executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    return RewardManager.claim(p, RewardType.MONTHLY_STAFF) ? 1 : 0;
                                }))
                        )
                )

                // ---- Admin-Teil: Permission evolutionboost.rewards.admin ----
                .then(Commands.literal("list")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.rewards.admin", 2, false))
                        .then(Commands.literal("donator").executes(ctx -> {
                            RewardManager.sendRoleList(ctx.getSource(), "donator");
                            return 1;
                        }))
                        .then(Commands.literal("donator_copper").executes(ctx -> {
                            RewardManager.sendRoleList(ctx.getSource(), "donator_copper");
                            return 1;
                        }))
                        .then(Commands.literal("donator_silver").executes(ctx -> {
                            RewardManager.sendRoleList(ctx.getSource(), "donator_silver");
                            return 1;
                        }))
                        .then(Commands.literal("donator_gold").executes(ctx -> {
                            RewardManager.sendRoleList(ctx.getSource(), "donator_gold");
                            return 1;
                        }))
                        .then(Commands.literal("gym").executes(ctx -> {
                            RewardManager.sendRoleList(ctx.getSource(), "gym");
                            return 1;
                        }))
                        .then(Commands.literal("staff").executes(ctx -> {
                            RewardManager.sendRoleList(ctx.getSource(), "staff");
                            return 1;
                        }))
                )

                // ---- Admin: Set Eligibility – jetzt mit Offline-Namen ----
                .then(Commands.literal("set")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.rewards.admin", 2, false))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("donator")
                                        // Backwards-Compat: true = COPPER, false = NONE
                                        .then(Commands.literal("true").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setDonatorEligibility(name, true);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR (Copper) for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": true").withStyle(ChatFormatting.GREEN)), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("false").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setDonatorEligibility(name, false);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": false").withStyle(ChatFormatting.RED)), false);
                                            return 1;
                                        }))

                                        // Neue, explizite Tiers
                                        .then(Commands.literal("copper").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setDonatorTier(name, DonatorTier.COPPER);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR TIER for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": COPPER").withStyle(ChatFormatting.GOLD)), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("silver").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setDonatorTier(name, DonatorTier.SILVER);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR TIER for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": SILVER").withStyle(ChatFormatting.AQUA)), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("gold").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setDonatorTier(name, DonatorTier.GOLD);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR TIER for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": GOLD").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("none").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setDonatorTier(name, DonatorTier.NONE);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set DONATOR TIER for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": NONE").withStyle(ChatFormatting.RED)), false);
                                            return 1;
                                        }))
                                )
                                .then(Commands.literal("gym")
                                        .then(Commands.literal("true").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setGymEligibility(name, true);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set GYM for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": true").withStyle(ChatFormatting.GREEN)), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("false").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setGymEligibility(name, false);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set GYM for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": false").withStyle(ChatFormatting.RED)), false);
                                            return 1;
                                        }))
                                )
                                .then(Commands.literal("staff")
                                        .then(Commands.literal("true").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setStaffEligibility(name, true);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set STAFF for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": true").withStyle(ChatFormatting.GREEN)), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("false").executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            RewardManager.setStaffEligibility(name, false);

                                            ServerPlayer online = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                                            Component targetName = online != null ? online.getName() : Component.literal(name);

                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Set STAFF for ")
                                                    .append(targetName)
                                                    .append(Component.literal(": false").withStyle(ChatFormatting.RED)), false);
                                            return 1;
                                        }))
                                )
                        )
                )

                // ---- Admin: Reset (braucht weiterhin Online-Spieler / UUID) ----
                .then(Commands.literal("reset")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.rewards.admin", 2, false))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.literal("daily").executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    RewardManager.resetCooldown(t.getUUID(), RewardType.DAILY);
                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset DAILY for ").append(t.getName()), false);
                                    return 1;
                                }))
                                .then(Commands.literal("weekly").executes(ctx -> {
                                    ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                    RewardManager.resetCooldown(t.getUUID(), RewardType.WEEKLY);
                                    ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset WEEKLY for ").append(t.getName()), false);
                                    return 1;
                                }))
                                .then(Commands.literal("monthly")
                                        .then(Commands.literal("donator").executes(ctx -> {
                                            ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                            RewardManager.resetCooldown(t.getUUID(), RewardType.MONTHLY_DONATOR);
                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset MONTHLY (DONATOR) for ").append(t.getName()), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("gym").executes(ctx -> {
                                            ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                            RewardManager.resetCooldown(t.getUUID(), RewardType.MONTHLY_GYM);
                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset MONTHLY (GYM) for ").append(t.getName()), false);
                                            return 1;
                                        }))
                                        .then(Commands.literal("staff").executes(ctx -> {
                                            ServerPlayer t = EntityArgument.getPlayer(ctx, "player");
                                            RewardManager.resetCooldown(t.getUUID(), RewardType.MONTHLY_STAFF);
                                            ctx.getSource().sendSuccess(() -> Component.literal("[Rewards] Reset MONTHLY (STAFF) for ").append(t.getName()), false);
                                            return 1;
                                        }))
                                )
                        )
                )

                // ---- Admin: Reload Configs ----
                .then(Commands.literal("reload")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.rewards.admin", 2, false))
                        .executes(ctx -> {
                            // Rewards (rewards.json) & Eligibility (eligibility.properties) neu laden
                            RewardConfig.loadOrCreate();
                            RewardManager.reloadEligibilityFromDisk();

                            ctx.getSource().sendSuccess(() ->
                                            Component.literal("[Rewards] Reloaded rewards.json and eligibility.properties.")
                                                    .withStyle(ChatFormatting.GREEN),
                                    false);
                            return 1;
                        })
                );

        // unter /evolutionboost & /eb anhängen
        d.register(Commands.literal("evolutionboost").then(subtree));
        d.register(Commands.literal("eb").then(subtree));
    }
}
