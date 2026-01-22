package com.ichezzy.evolutionboost.gym;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Commands für das Gym-System.
 * 
 * Admin Commands:
 *   /eb gym <type> setleader <player>  - Setzt einen Leader
 *   /eb gym <type> removeleader        - Entfernt den Leader
 *   /eb gym <type> info                - Zeigt Gym-Info
 *   /eb gym <type> rewards set <badge> <tm> <coins> - Setzt Rewards
 *   /eb gym list                       - Listet alle Gyms
 *   /eb gym history                    - Zeigt Leader-Historie
 *   /eb gym reload                     - Lädt Config neu
 * 
 * Player Commands (später):
 *   /eb gym <type> challenge           - Fordert Leader heraus
 *   /eb gym accept                     - Akzeptiert Challenge (Leader)
 *   /eb gym decline                    - Lehnt Challenge ab (Leader)
 *   /eb gym stats [player]             - Zeigt Stats
 */
public final class GymCommand {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));

    // Suggestions für Gym-Typen
    private static final SuggestionProvider<CommandSourceStack> GYM_TYPE_SUGGESTIONS = (ctx, builder) -> {
        List<String> types = Arrays.stream(GymType.values())
                .map(t -> t.getId())
                .toList();
        return SharedSuggestionProvider.suggest(types, builder);
    };

    private GymCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("evolutionboost")
                .then(Commands.literal("gym")
                    // /eb gym list
                    .then(Commands.literal("list")
                        .executes(GymCommand::listGyms))
                    
                    // /eb gym accept (Leader akzeptiert Challenge)
                    .then(Commands.literal("accept")
                        .executes(GymCommand::acceptChallenge))
                    
                    // /eb gym decline (Leader lehnt Challenge ab)
                    .then(Commands.literal("decline")
                        .executes(GymCommand::declineChallenge))
                    
                    // /eb gym stats [player]
                    .then(Commands.literal("stats")
                        .executes(GymCommand::showOwnStats)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(GymCommand::showPlayerStats)))
                    
                    // /eb gym history [lines]
                    .then(Commands.literal("history")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> showHistory(ctx, 20))
                        .then(Commands.argument("lines", StringArgumentType.word())
                            .executes(ctx -> showHistory(ctx, 
                                Integer.parseInt(StringArgumentType.getString(ctx, "lines"))))))
                    
                    // /eb gym reload
                    .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(GymCommand::reloadConfig))
                    
                    // /eb gym <type> ...
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(GYM_TYPE_SUGGESTIONS)
                        
                        // /eb gym <type> challenge (Spieler fordert Leader heraus)
                        .then(Commands.literal("challenge")
                            .executes(GymCommand::challengeGym))
                        
                        // /eb gym <type> info
                        .then(Commands.literal("info")
                            .executes(GymCommand::showGymInfo))
                        
                        // /eb gym <type> setleader <player>
                        .then(Commands.literal("setleader")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(GymCommand::setLeader)))
                        
                        // /eb gym <type> removeleader [reason]
                        .then(Commands.literal("removeleader")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> removeLeader(ctx, "No reason given"))
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> removeLeader(ctx, 
                                    StringArgumentType.getString(ctx, "reason")))))
                        
                        // /eb gym <type> rewards set <badge> <tm> <coins>
                        .then(Commands.literal("rewards")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.literal("set")
                                .then(Commands.argument("badge", StringArgumentType.word())
                                    .then(Commands.argument("tm", StringArgumentType.word())
                                        .then(Commands.argument("coins", StringArgumentType.word())
                                            .executes(GymCommand::setRewards))))))
                        
                        // /eb gym <type> enable/disable
                        .then(Commands.literal("enable")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> setEnabled(ctx, true)))
                        .then(Commands.literal("disable")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> setEnabled(ctx, false)))
                    ))
        );

        // Alias /eb
        dispatcher.register(
            Commands.literal("eb")
                .then(Commands.literal("gym")
                    .then(Commands.literal("list")
                        .executes(GymCommand::listGyms))
                    .then(Commands.literal("accept")
                        .executes(GymCommand::acceptChallenge))
                    .then(Commands.literal("decline")
                        .executes(GymCommand::declineChallenge))
                    .then(Commands.literal("stats")
                        .executes(GymCommand::showOwnStats)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(GymCommand::showPlayerStats)))
                    .then(Commands.literal("history")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> showHistory(ctx, 20))
                        .then(Commands.argument("lines", StringArgumentType.word())
                            .executes(ctx -> showHistory(ctx, 
                                Integer.parseInt(StringArgumentType.getString(ctx, "lines"))))))
                    .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(2))
                        .executes(GymCommand::reloadConfig))
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests(GYM_TYPE_SUGGESTIONS)
                        .then(Commands.literal("challenge")
                            .executes(GymCommand::challengeGym))
                        .then(Commands.literal("info")
                            .executes(GymCommand::showGymInfo))
                        .then(Commands.literal("setleader")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(GymCommand::setLeader)))
                        .then(Commands.literal("removeleader")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> removeLeader(ctx, "No reason given"))
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> removeLeader(ctx, 
                                    StringArgumentType.getString(ctx, "reason")))))
                        .then(Commands.literal("rewards")
                            .requires(src -> src.hasPermission(2))
                            .then(Commands.literal("set")
                                .then(Commands.argument("badge", StringArgumentType.word())
                                    .then(Commands.argument("tm", StringArgumentType.word())
                                        .then(Commands.argument("coins", StringArgumentType.word())
                                            .executes(GymCommand::setRewards))))))
                        .then(Commands.literal("enable")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> setEnabled(ctx, true)))
                        .then(Commands.literal("disable")
                            .requires(src -> src.hasPermission(2))
                            .executes(ctx -> setEnabled(ctx, false)))
                    ))
        );
    }

    // ==================== Command Handlers ====================

    private static int listGyms(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        GymConfig cfg = GymConfig.get();

        src.sendSuccess(() -> Component.literal("══════ Gym Overview ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (GymType type : GymType.values()) {
            GymConfig.GymEntry gym = cfg.getGym(type);
            if (gym == null) continue;

            MutableComponent line = Component.literal("  ");
            
            // Status-Indikator
            if (!gym.enabled) {
                line.append(Component.literal("✗ ").withStyle(ChatFormatting.DARK_GRAY));
            } else if (gym.currentLeader != null) {
                line.append(Component.literal("✓ ").withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.literal("○ ").withStyle(ChatFormatting.YELLOW));
            }

            // Gym-Name
            line.append(Component.literal(type.getDisplayName() + " Gym")
                    .withStyle(type.getColor()));

            // Leader
            if (gym.currentLeader != null) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(gym.currentLeader).withStyle(ChatFormatting.WHITE));
            } else if (gym.enabled) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal("No Leader").withStyle(ChatFormatting.YELLOW));
            } else {
                line.append(Component.literal(" (disabled)").withStyle(ChatFormatting.DARK_GRAY));
            }

            // Klickbar machen
            line.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                            "/eb gym " + type.getId() + " info"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click for details"))));

            src.sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int showGymInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "type");
        GymType type = GymType.fromId(typeId);

        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null) {
            src.sendFailure(Component.literal("Gym not configured: " + typeId));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("══════ " + type.getDisplayName() + " Gym ══════")
                .withStyle(type.getColor(), ChatFormatting.BOLD), false);

        // Status
        src.sendSuccess(() -> Component.literal("  Status: ")
                .withStyle(ChatFormatting.GRAY)
                .append(gym.enabled 
                        ? Component.literal("Enabled").withStyle(ChatFormatting.GREEN)
                        : Component.literal("Disabled").withStyle(ChatFormatting.RED)), false);

        // Leader
        if (gym.currentLeader != null) {
            src.sendSuccess(() -> Component.literal("  Leader: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(gym.currentLeader).withStyle(ChatFormatting.WHITE)), false);

            // Leader seit
            if (gym.leaderStartDate != null) {
                try {
                    Instant start = Instant.parse(gym.leaderStartDate);
                    String dateStr = DATE_FMT.format(start);
                    src.sendSuccess(() -> Component.literal("  Since: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(dateStr).withStyle(ChatFormatting.WHITE)), false);
                } catch (Exception ignored) {}
            }

            // Leader Stats
            String leaderUUID = gym.currentLeaderUUID;
            if (leaderUUID != null) {
                int battles = GymData.get().getLeaderBattlesThisMonth(leaderUUID);
                int required = GymConfig.get().leaderMinBattlesForMonthlyReward;
                ChatFormatting color = battles >= required ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
                
                src.sendSuccess(() -> Component.literal("  Battles this month: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(battles + "/" + required).withStyle(color)), false);
            }
        } else {
            src.sendSuccess(() -> Component.literal("  Leader: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("None").withStyle(ChatFormatting.YELLOW)), false);
        }

        // Rewards
        src.sendSuccess(() -> Component.literal("  Rewards:").withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("    Badge: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(gym.rewards.badge.isEmpty() ? "Not set" : gym.rewards.badge)
                        .withStyle(ChatFormatting.WHITE)), false);
        src.sendSuccess(() -> Component.literal("    TM: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(gym.rewards.tm.isEmpty() ? "Not set" : gym.rewards.tm)
                        .withStyle(ChatFormatting.WHITE)), false);
        src.sendSuccess(() -> Component.literal("    Silver Coins: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(gym.rewards.silverCoins))
                        .withStyle(ChatFormatting.WHITE)), false);

        return 1;
    }

    private static int setLeader(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "type");
        GymType type = GymType.fromId(typeId);

        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String setBy = src.getTextName();

            boolean success = GymManager.get().setLeader(type, target, setBy);
            if (success) {
                src.sendSuccess(() -> Component.literal("✓ ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(target.getGameProfile().getName())
                                .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" is now the ")
                                .withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(type.getDisplayName() + " Gym Leader")
                                .withStyle(type.getColor())), true);
            } else {
                src.sendFailure(Component.literal("Failed to set leader"));
            }
            return success ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    private static int removeLeader(CommandContext<CommandSourceStack> ctx, String reason) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "type");
        GymType type = GymType.fromId(typeId);

        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        String currentLeader = GymManager.get().getLeaderName(type);
        if (currentLeader == null) {
            src.sendFailure(Component.literal(type.getDisplayName() + " Gym has no leader"));
            return 0;
        }

        String removedBy = src.getTextName();
        boolean success = GymManager.get().removeLeader(type, removedBy, reason);

        if (success) {
            src.sendSuccess(() -> Component.literal("✓ ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Removed ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(currentLeader)
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" as ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(type.getDisplayName() + " Gym Leader")
                            .withStyle(type.getColor())), true);
        } else {
            src.sendFailure(Component.literal("Failed to remove leader"));
        }

        return success ? 1 : 0;
    }

    private static int setRewards(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "type");
        GymType type = GymType.fromId(typeId);

        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        String badge = StringArgumentType.getString(ctx, "badge");
        String tm = StringArgumentType.getString(ctx, "tm");
        int coins;
        try {
            coins = Integer.parseInt(StringArgumentType.getString(ctx, "coins"));
        } catch (NumberFormatException e) {
            src.sendFailure(Component.literal("Invalid coin amount"));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null) {
            src.sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        gym.rewards.badge = badge;
        gym.rewards.tm = tm;
        gym.rewards.silverCoins = coins;
        GymConfig.save();

        src.sendSuccess(() -> Component.literal("✓ ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal("Updated rewards for ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(type.getDisplayName() + " Gym")
                        .withStyle(type.getColor())), false);

        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "type");
        GymType type = GymType.fromId(typeId);

        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null) {
            src.sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        gym.enabled = enabled;
        GymConfig.save();

        src.sendSuccess(() -> Component.literal("✓ ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(type.getDisplayName() + " Gym ")
                        .withStyle(type.getColor()))
                .append(Component.literal(enabled ? "enabled" : "disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);

        return 1;
    }

    private static int showHistory(CommandContext<CommandSourceStack> ctx, int lines) {
        CommandSourceStack src = ctx.getSource();
        String history = GymLogManager.getLeaderHistoryTail(lines);

        src.sendSuccess(() -> Component.literal("══════ Leader History ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (String line : history.split("\n")) {
            if (!line.isBlank()) {
                src.sendSuccess(() -> Component.literal(line)
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        GymConfig.reload();
        src.sendSuccess(() -> Component.literal("✓ Gym config reloaded")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ==================== Player Commands ====================

    private static int challengeGym(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can challenge gyms"));
            return 0;
        }

        String typeId = StringArgumentType.getString(ctx, "type");
        GymType type = GymType.fromId(typeId);

        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        String error = GymManager.get().createChallenge(player, type);
        if (error != null) {
            src.sendFailure(Component.literal(error));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("⚔ ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Challenge sent to ")
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(type.getDisplayName() + " Gym Leader")
                        .withStyle(type.getColor()))
                .append(Component.literal("!")
                        .withStyle(ChatFormatting.WHITE)), false);
        
        src.sendSuccess(() -> Component.literal("  Waiting for response... (")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(GymConfig.get().challengeTimeoutSeconds + "s")
                        .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" timeout)")
                        .withStyle(ChatFormatting.GRAY)), false);

        return 1;
    }

    private static int acceptChallenge(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        String error = GymManager.get().acceptChallenge(player);
        if (error != null) {
            src.sendFailure(Component.literal(error));
            return 0;
        }

        return 1;
    }

    private static int declineChallenge(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }

        String error = GymManager.get().declineChallenge(player);
        if (error != null) {
            src.sendFailure(Component.literal(error));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("✓ Challenge declined")
                .withStyle(ChatFormatting.YELLOW), false);

        return 1;
    }

    private static int showOwnStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Only players can view stats"));
            return 0;
        }

        return showStats(src, player.getStringUUID(), player.getGameProfile().getName());
    }

    private static int showPlayerStats(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            return showStats(src, target.getStringUUID(), target.getGameProfile().getName());
        } catch (Exception e) {
            src.sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    private static int showStats(CommandSourceStack src, String uuid, String name) {
        GymData.PlayerGymStats stats = GymData.get().getPlayerStats(uuid);

        src.sendSuccess(() -> Component.literal("══════ Gym Stats: " + name + " ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        // Challenger Stats
        src.sendSuccess(() -> Component.literal("  As Challenger:")
                .withStyle(ChatFormatting.YELLOW), false);
        src.sendSuccess(() -> Component.literal("    Battles: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(stats.totalBattles))
                        .withStyle(ChatFormatting.WHITE)), false);
        src.sendSuccess(() -> Component.literal("    Wins: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(stats.wins))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" / Losses: ")
                        .withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(stats.losses))
                        .withStyle(ChatFormatting.RED)), false);
        
        if (stats.totalBattles > 0) {
            src.sendSuccess(() -> Component.literal("    Win Rate: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format("%.1f%%", stats.getWinRate()))
                            .withStyle(ChatFormatting.AQUA)), false);
        }

        // Badges
        if (!stats.badgesEarned.isEmpty()) {
            MutableComponent badgeLine = Component.literal("    Badges: ")
                    .withStyle(ChatFormatting.GRAY);
            boolean first = true;
            for (String gymId : stats.badgesEarned) {
                GymType type = GymType.fromId(gymId);
                if (type != null) {
                    if (!first) badgeLine.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                    badgeLine.append(Component.literal(type.getDisplayName()).withStyle(type.getColor()));
                    first = false;
                }
            }
            src.sendSuccess(() -> badgeLine, false);
        }

        // Leader Stats (wenn vorhanden)
        if (stats.battlesAsLeader > 0) {
            src.sendSuccess(() -> Component.literal("  As Leader:")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            src.sendSuccess(() -> Component.literal("    Battles: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(stats.battlesAsLeader))
                            .withStyle(ChatFormatting.WHITE)), false);
            src.sendSuccess(() -> Component.literal("    Wins: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(stats.winsAsLeader))
                            .withStyle(ChatFormatting.GREEN)), false);
        }

        // Monthly Reward Status für Leader
        GymType leaderGym = GymManager.get().getLeaderGym(
                src.getServer().getPlayerList().getPlayer(java.util.UUID.fromString(uuid)));
        if (leaderGym != null) {
            int battles = GymData.get().getLeaderBattlesThisMonth(uuid);
            int required = GymConfig.get().leaderMinBattlesForMonthlyReward;
            
            src.sendSuccess(() -> Component.literal("  Monthly Reward Progress: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(battles + "/" + required)
                            .withStyle(battles >= required ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .append(Component.literal(" battles")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        return 1;
    }
}
