package com.ichezzy.evolutionboost.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.ichezzy.evolutionboost.configs.GymConfig;
import com.ichezzy.evolutionboost.gym.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import java.util.*;

/**
 * Commands für das Gym-System.
 * 
 * Player Commands:
 *   /eb gym list                              - Listet alle Gyms
 *   /eb gym info <gymtype>                    - Zeigt Gym-Info
 *   /eb gym challenge <gymtype>               - Fordert Leader heraus
 *   /eb gym accept                            - Leader akzeptiert Challenge
 *   /eb gym decline                           - Leader lehnt Challenge ab
 *   /eb gym stats [player]                    - Zeigt Stats
 * 
 * Leader Commands:
 *   /eb gym register <gymtype>                - Registriert Team
 *   /eb gym rules <gymtype> set <format> <levelcap> - Setzt Regeln
 *   /eb gym rules <gymtype> info              - Zeigt aktuelle Regeln
 * 
 * Admin Commands:
 *   /eb gym admin setleader <gymtype> <player>
 *   /eb gym admin removeleader <gymtype> [reason]
 *   /eb gym admin rewards <gymtype> <badge> <tm> <coins>
 *   /eb gym admin info <gymtype>
 *   /eb gym admin enable/disable <gymtype>
 *   /eb gym admin resetstats <player|all>
 *   /eb gym admin resetteam <gymtype>
 *   /eb gym admin rules <gymtype> set <format> <levelcap>
 *   /eb gym admin rules <gymtype> reset
 *   /eb gym admin history [lines]
 *   /eb gym admin reload
 */
public final class GymCommand {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Berlin"));

    private static final SuggestionProvider<CommandSourceStack> GYM_TYPE_SUGGESTIONS = (ctx, builder) -> {
        List<String> types = Arrays.stream(GymType.values())
                .map(GymType::getId)
                .toList();
        return SharedSuggestionProvider.suggest(types, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> FORMAT_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(List.of("singles", "doubles"), builder);

    private static final SuggestionProvider<CommandSourceStack> LEVEL_CAP_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(List.of("50", "100"), builder);

    private GymCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var gymNode = Commands.literal("gym")
                // ==================== Player Commands ====================
                .then(Commands.literal("list")
                        .executes(GymCommand::listGyms))
                
                .then(Commands.literal("info")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .executes(GymCommand::showGymInfo)))
                
                .then(Commands.literal("challenge")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .executes(GymCommand::challengeGym)))
                
                .then(Commands.literal("accept")
                        .executes(GymCommand::acceptChallenge))
                
                .then(Commands.literal("decline")
                        .executes(GymCommand::declineChallenge))
                
                .then(Commands.literal("stats")
                        .executes(GymCommand::showOwnStats)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showPlayerStatsWrapper(ctx))))
                
                // ==================== Leader Commands ====================
                .then(Commands.literal("register")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .executes(GymCommand::registerTeam)))
                
                .then(Commands.literal("rules")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                // /eb gym rules <gymtype> info
                                .then(Commands.literal("info")
                                        .executes(GymCommand::showRulesInfo))
                                // /eb gym rules <gymtype> set <format> <levelcap>
                                .then(Commands.literal("set")
                                        .then(Commands.argument("format", StringArgumentType.word())
                                                .suggests(FORMAT_SUGGESTIONS)
                                                .then(Commands.argument("levelcap", IntegerArgumentType.integer(50, 100))
                                                        .suggests(LEVEL_CAP_SUGGESTIONS)
                                                        .executes(GymCommand::setRulesAsLeader))))))
                
                // ==================== Admin Commands ====================
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        
                        .then(Commands.literal("setleader")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> setLeaderWrapper(ctx)))))
                        
                        .then(Commands.literal("removeleader")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(ctx -> removeLeader(ctx, "No reason given"))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> removeLeader(ctx, 
                                                        StringArgumentType.getString(ctx, "reason"))))))
                        
                        .then(Commands.literal("rewards")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("badge", StringArgumentType.word())
                                                .then(Commands.argument("tm", StringArgumentType.word())
                                                        .then(Commands.argument("coins", IntegerArgumentType.integer(0))
                                                                .executes(GymCommand::setRewards))))))
                        
                        .then(Commands.literal("info")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(GymCommand::showAdminGymInfo)))
                        
                        .then(Commands.literal("enable")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(ctx -> setEnabled(ctx, true))))
                        
                        .then(Commands.literal("disable")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(ctx -> setEnabled(ctx, false))))
                        
                        .then(Commands.literal("resetstats")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(GymCommand::resetStats)))
                        
                        .then(Commands.literal("resetteam")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(GymCommand::adminResetTeam)))
                        
                        .then(Commands.literal("rules")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("format", StringArgumentType.word())
                                                        .suggests(FORMAT_SUGGESTIONS)
                                                        .then(Commands.argument("levelcap", IntegerArgumentType.integer(50, 100))
                                                                .suggests(LEVEL_CAP_SUGGESTIONS)
                                                                .executes(GymCommand::adminSetRules))))
                                        .then(Commands.literal("reset")
                                                .executes(GymCommand::adminResetRules))))
                        
                        .then(Commands.literal("history")
                                .executes(ctx -> showHistory(ctx, 20))
                                .then(Commands.argument("lines", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> showHistory(ctx, 
                                                IntegerArgumentType.getInteger(ctx, "lines")))))
                        
                        .then(Commands.literal("reload")
                                .executes(GymCommand::reloadConfig)));

        dispatcher.register(Commands.literal("evolutionboost").then(gymNode));
        
        // Alias /eb
        dispatcher.register(Commands.literal("eb").then(buildGymAlias()));
    }

    /**
     * Baut den /eb gym Alias-Baum (Brigadier erfordert separate Registrierung).
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildGymAlias() {
        return Commands.literal("gym")
                .then(Commands.literal("list").executes(GymCommand::listGyms))
                .then(Commands.literal("info")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .executes(GymCommand::showGymInfo)))
                .then(Commands.literal("challenge")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .executes(GymCommand::challengeGym)))
                .then(Commands.literal("accept").executes(GymCommand::acceptChallenge))
                .then(Commands.literal("decline").executes(GymCommand::declineChallenge))
                .then(Commands.literal("stats")
                        .executes(GymCommand::showOwnStats)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> showPlayerStatsWrapper(ctx))))
                .then(Commands.literal("register")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .executes(GymCommand::registerTeam)))
                .then(Commands.literal("rules")
                        .then(Commands.argument("gymtype", StringArgumentType.word())
                                .suggests(GYM_TYPE_SUGGESTIONS)
                                .then(Commands.literal("info").executes(GymCommand::showRulesInfo))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("format", StringArgumentType.word())
                                                .suggests(FORMAT_SUGGESTIONS)
                                                .then(Commands.argument("levelcap", IntegerArgumentType.integer(50, 100))
                                                        .suggests(LEVEL_CAP_SUGGESTIONS)
                                                        .executes(GymCommand::setRulesAsLeader))))))
                .then(Commands.literal("admin")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("setleader")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> setLeaderWrapper(ctx)))))
                        .then(Commands.literal("removeleader")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(ctx -> removeLeader(ctx, "No reason given"))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> removeLeader(ctx, 
                                                        StringArgumentType.getString(ctx, "reason"))))))
                        .then(Commands.literal("rewards")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .then(Commands.argument("badge", StringArgumentType.word())
                                                .then(Commands.argument("tm", StringArgumentType.word())
                                                        .then(Commands.argument("coins", IntegerArgumentType.integer(0))
                                                                .executes(GymCommand::setRewards))))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(GymCommand::showAdminGymInfo)))
                        .then(Commands.literal("enable")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(ctx -> setEnabled(ctx, true))))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(ctx -> setEnabled(ctx, false))))
                        .then(Commands.literal("resetstats")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(GymCommand::resetStats)))
                        .then(Commands.literal("resetteam")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .executes(GymCommand::adminResetTeam)))
                        .then(Commands.literal("rules")
                                .then(Commands.argument("gymtype", StringArgumentType.word())
                                        .suggests(GYM_TYPE_SUGGESTIONS)
                                        .then(Commands.literal("set")
                                                .then(Commands.argument("format", StringArgumentType.word())
                                                        .suggests(FORMAT_SUGGESTIONS)
                                                        .then(Commands.argument("levelcap", IntegerArgumentType.integer(50, 100))
                                                                .suggests(LEVEL_CAP_SUGGESTIONS)
                                                                .executes(GymCommand::adminSetRules))))
                                        .then(Commands.literal("reset")
                                                .executes(GymCommand::adminResetRules))))
                        .then(Commands.literal("history")
                                .executes(ctx -> showHistory(ctx, 20))
                                .then(Commands.argument("lines", IntegerArgumentType.integer(1, 100))
                                        .executes(ctx -> showHistory(ctx, 
                                                IntegerArgumentType.getInteger(ctx, "lines")))))
                        .then(Commands.literal("reload")
                                .executes(GymCommand::reloadConfig)));
    }

    // ==================== Helper Methods ====================

    /**
     * Prüft ob ein Spieler der Leader eines bestimmten Gyms ist.
     */
    private static boolean isLeaderOf(ServerPlayer player, String gymTypeId) {
        GymConfig.GymEntry gym = GymConfig.get().getGym(gymTypeId);
        return gym != null && player.getStringUUID().equals(gym.currentLeaderUUID);
    }

    private static int showPlayerStatsWrapper(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            return showStatsFor(ctx.getSource(), target.getStringUUID(), target.getName().getString());
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    private static int setLeaderWrapper(CommandContext<CommandSourceStack> ctx) {
        try {
            return setLeader(ctx);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Player not found"));
            return 0;
        }
    }

    // ==================== Player Commands ====================

    private static int listGyms(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        GymConfig cfg = GymConfig.get();
        GymData data = GymData.get();

        src.sendSuccess(() -> Component.literal("══════ Gym Overview ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (GymType type : GymType.values()) {
            GymConfig.GymEntry gym = cfg.getGym(type);
            if (gym == null) continue;

            MutableComponent line = Component.literal("  ");
            
            if (!gym.enabled) {
                line.append(Component.literal("✗ ").withStyle(ChatFormatting.DARK_GRAY));
            } else if (gym.currentLeader != null && gym.leaderRegistered) {
                line.append(Component.literal("✓ ").withStyle(ChatFormatting.GREEN));
            } else if (gym.currentLeader != null) {
                line.append(Component.literal("⚠ ").withStyle(ChatFormatting.YELLOW));
            } else {
                line.append(Component.literal("○ ").withStyle(ChatFormatting.GRAY));
            }

            line.append(Component.literal(type.getDisplayName() + " Gym")
                    .withStyle(type.getColor()));

            if (gym.currentLeader != null) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal(gym.currentLeader).withStyle(ChatFormatting.WHITE));
                
                // Format und Level-Cap anzeigen
                GymData.LeaderTeamData teamData = data.getLeaderTeam(type.getId());
                if (teamData != null) {
                    String format = teamData.battleFormat != null ? teamData.battleFormat : "singles";
                    int levelCap = teamData.levelCap > 0 ? teamData.levelCap : 50;
                    line.append(Component.literal(" [" + format + " Lv." + levelCap + "]")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                
                if (!gym.leaderRegistered) {
                    line.append(Component.literal(" (not registered)")
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else if (gym.enabled) {
                line.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY));
                line.append(Component.literal("No Leader").withStyle(ChatFormatting.GRAY));
            } else {
                line.append(Component.literal(" (disabled)").withStyle(ChatFormatting.DARK_GRAY));
            }

            line.withStyle(style -> style
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                            "/eb gym info " + type.getId()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click for details"))));

            src.sendSuccess(() -> line, false);
        }

        return 1;
    }

    private static int showGymInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "gymtype");
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

        GymData.LeaderTeamData teamData = GymData.get().getLeaderTeam(type.getId());

        src.sendSuccess(() -> Component.literal("══════ " + gym.displayName + " ══════")
                .withStyle(type.getColor(), ChatFormatting.BOLD), false);

        String status = !gym.enabled ? "Disabled" : 
                       (gym.currentLeader == null ? "No Leader" : 
                       (gym.leaderRegistered ? "Active" : "Awaiting Registration"));
        ChatFormatting statusColor = !gym.enabled ? ChatFormatting.RED :
                                    (gym.currentLeader == null ? ChatFormatting.GRAY :
                                    (gym.leaderRegistered ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        src.sendSuccess(() -> Component.literal("  Status: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(status).withStyle(statusColor)), false);

        if (gym.currentLeader != null) {
            src.sendSuccess(() -> Component.literal("  Leader: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(gym.currentLeader).withStyle(ChatFormatting.WHITE)), false);
        }

        // Rules anzeigen
        if (teamData != null) {
            String format = teamData.battleFormat != null ? teamData.battleFormat : "singles";
            int levelCap = teamData.levelCap > 0 ? teamData.levelCap : 50;
            src.sendSuccess(() -> Component.literal("  Format: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(format.substring(0, 1).toUpperCase() + format.substring(1))
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" | Level Cap: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(levelCap))
                            .withStyle(ChatFormatting.AQUA)), false);
        }

        src.sendSuccess(() -> Component.literal("  Rewards: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(gym.rewards.silverCoins + " Silver Coins")
                        .withStyle(ChatFormatting.WHITE)), false);

        if (gym.enabled && gym.currentLeader != null && gym.leaderRegistered) {
            src.sendSuccess(() -> Component.literal("")
                    .append(Component.literal("  → ")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("/eb gym challenge " + type.getId())
                            .withStyle(ChatFormatting.GREEN)
                            .withStyle(style -> style
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                                            "/eb gym challenge " + type.getId())))), false);
        }

        return 1;
    }

    private static int showRulesInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            src.sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymData.LeaderTeamData teamData = GymData.get().getLeaderTeam(typeId);
        GymConfig.GymEntry gym = GymConfig.get().getGym(type);

        src.sendSuccess(() -> Component.literal("══════ " + type.getDisplayName() + " Gym Rules ══════")
                .withStyle(type.getColor(), ChatFormatting.BOLD), false);

        if (teamData == null) {
            src.sendSuccess(() -> Component.literal("  No rules configured yet.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        String format = teamData.battleFormat != null ? teamData.battleFormat : "singles";
        int levelCap = teamData.levelCap > 0 ? teamData.levelCap : 50;

        src.sendSuccess(() -> Component.literal("  Battle Format: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(format.substring(0, 1).toUpperCase() + format.substring(1))
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)), false);

        src.sendSuccess(() -> Component.literal("  Level Cap: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(levelCap))
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)), false);

        if (gym != null && gym.currentLeader != null) {
            src.sendSuccess(() -> Component.literal("  Leader: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(gym.currentLeader)
                            .withStyle(ChatFormatting.WHITE)), false);
        }

        return 1;
    }

    private static int challengeGym(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can challenge gyms"));
            return 0;
        }

        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig cfg = GymConfig.get();
        GymConfig.GymEntry gym = cfg.getGym(type);
        
        if (gym == null || !gym.enabled) {
            ctx.getSource().sendFailure(Component.literal("This gym is not available"));
            return 0;
        }

        if (gym.currentLeader == null) {
            ctx.getSource().sendFailure(Component.literal("This gym has no leader"));
            return 0;
        }

        if (!gym.leaderRegistered && cfg.requireTeamRegistration) {
            ctx.getSource().sendFailure(Component.literal("The gym leader has not registered their team yet"));
            return 0;
        }

        if (player.getStringUUID().equals(gym.currentLeaderUUID)) {
            ctx.getSource().sendFailure(Component.literal("You cannot challenge yourself!"));
            return 0;
        }

        ServerPlayer leader = player.getServer().getPlayerList().getPlayer(UUID.fromString(gym.currentLeaderUUID));
        if (leader == null) {
            ctx.getSource().sendFailure(Component.literal("The gym leader is not online"));
            return 0;
        }

        double distance = player.distanceTo(leader);
        if (distance > cfg.challengeRadius) {
            ctx.getSource().sendFailure(Component.literal("You must be within " + cfg.challengeRadius + 
                    " blocks of the leader (current: " + (int)distance + ")"));
            return 0;
        }

        GymChallengeManager.sendChallenge(player, leader, type);
        return 1;
    }

    private static int acceptChallenge(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }
        return GymChallengeManager.acceptChallenge(player);
    }

    private static int declineChallenge(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can use this command"));
            return 0;
        }
        return GymChallengeManager.declineChallenge(player);
    }

    // ==================== Leader Commands ====================

    private static int registerTeam(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can register teams"));
            return 0;
        }

        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig cfg = GymConfig.get();
        GymConfig.GymEntry gym = cfg.getGym(type);
        
        if (gym == null) {
            ctx.getSource().sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        // Prüfe ob Spieler der Leader ist
        if (!player.getStringUUID().equals(gym.currentLeaderUUID)) {
            ctx.getSource().sendFailure(Component.literal("You are not the leader of this gym"));
            return 0;
        }

        GymData data = GymData.get();
        
        // Prüfe ob Team-Änderung erlaubt ist
        if (gym.leaderRegistered && !data.canChangeTeam(typeId)) {
            int daysLeft = data.getDaysUntilTeamChange(typeId);
            ctx.getSource().sendFailure(Component.literal("You can change your team in " + daysLeft + " days"));
            return 0;
        }

        // Hole aktuelles Team
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        List<GymData.TeamPokemon> teamPokemon = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon != null) {
                String species = pokemon.getSpecies().getName().toLowerCase();
                String form = pokemon.getForm().getName();
                if (form.equalsIgnoreCase("normal") || form.equalsIgnoreCase("default")) {
                    form = "";
                }
                teamPokemon.add(new GymData.TeamPokemon(
                        species, form, pokemon.getLevel(), pokemon.getUuid().toString()
                ));
            }
        }

        if (teamPokemon.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("You need at least one Pokémon in your party"));
            return 0;
        }

        // Team registrieren
        data.registerLeaderTeam(typeId, player, teamPokemon);

        // Config updaten
        gym.leaderRegistered = true;
        GymConfig.save();

        // Erfolg
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Team registered for " + gym.displayName + "!")
                .withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Pokémon: " + teamPokemon.size())
                .withStyle(ChatFormatting.GRAY), false);
        
        // Hinweis auf Rules
        ctx.getSource().sendSuccess(() -> Component.literal("  Set battle rules with: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/eb gym rules " + typeId + " set <format> <levelcap>")
                        .withStyle(ChatFormatting.YELLOW)
                        .withStyle(style -> style.withClickEvent(
                                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, 
                                        "/eb gym rules " + typeId + " set singles 50")))), false);

        int changeDays = cfg.teamChangeIntervalDays;
        if (changeDays > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("  You can change your team in " + changeDays + " days")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    private static int setRulesAsLeader(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can set rules"));
            return 0;
        }

        String typeId = StringArgumentType.getString(ctx, "gymtype");
        String format = StringArgumentType.getString(ctx, "format").toLowerCase();
        int levelCap = IntegerArgumentType.getInteger(ctx, "levelcap");
        
        GymType type = GymType.fromId(typeId);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        // Prüfe ob Spieler der Leader ist
        if (!isLeaderOf(player, typeId)) {
            ctx.getSource().sendFailure(Component.literal("You are not the leader of this gym"));
            return 0;
        }

        // Validate format
        if (!format.equals("singles") && !format.equals("doubles")) {
            ctx.getSource().sendFailure(Component.literal("Invalid format. Use 'singles' or 'doubles'"));
            return 0;
        }

        // Validate level cap
        if (levelCap != 50 && levelCap != 100) {
            ctx.getSource().sendFailure(Component.literal("Level cap must be 50 or 100"));
            return 0;
        }

        GymData data = GymData.get();

        // Prüfe ob Rules-Änderung erlaubt ist
        if (!data.canChangeRules(typeId)) {
            int daysLeft = data.getDaysUntilRulesChange(typeId);
            ctx.getSource().sendFailure(Component.literal("You can change rules in " + daysLeft + " days"));
            return 0;
        }

        // Rules setzen
        data.setGymRules(typeId, format, levelCap);

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Rules updated for " + type.getDisplayName() + " Gym!")
                .withStyle(ChatFormatting.GREEN), false);
        ctx.getSource().sendSuccess(() -> Component.literal("  Format: " + format + " | Level Cap: " + levelCap)
                .withStyle(ChatFormatting.GRAY), false);

        int changeDays = GymConfig.get().teamChangeIntervalDays;
        if (changeDays > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("  You can change rules again in " + changeDays + " days")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    private static int showOwnStats(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Only players can view stats"));
            return 0;
        }
        return showStatsFor(ctx.getSource(), player.getStringUUID(), player.getName().getString());
    }

    private static int showStatsFor(CommandSourceStack src, String uuid, String name) {
        GymData.PlayerGymStats stats = GymData.get().getPlayerStats(uuid);

        src.sendSuccess(() -> Component.literal("══════ Gym Stats: " + name + " ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        src.sendSuccess(() -> Component.literal("  As Challenger:")
                .withStyle(ChatFormatting.AQUA), false);
        src.sendSuccess(() -> Component.literal("    Battles: " + stats.totalBattles)
                .withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("    Wins: " + stats.wins + " | Losses: " + stats.losses)
                .withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("    Win Rate: " + String.format("%.1f%%", stats.getWinRate()))
                .withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("    Badges: " + stats.badgesEarned.size())
                .withStyle(ChatFormatting.GRAY), false);

        if (stats.battlesAsLeader > 0) {
            src.sendSuccess(() -> Component.literal("  As Leader:")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            src.sendSuccess(() -> Component.literal("    Battles: " + stats.battlesAsLeader)
                    .withStyle(ChatFormatting.GRAY), false);
            src.sendSuccess(() -> Component.literal("    Wins: " + stats.winsAsLeader)
                    .withStyle(ChatFormatting.GRAY), false);
            src.sendSuccess(() -> Component.literal("    Win Rate: " + String.format("%.1f%%", stats.getLeaderWinRate()))
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    // ==================== Admin Commands ====================

    private static int setLeader(CommandContext<CommandSourceStack> ctx) throws Exception {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig cfg = GymConfig.get();
        GymConfig.GymEntry gym = cfg.getGym(type);
        
        if (gym == null) {
            ctx.getSource().sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        if (gym.currentLeaderUUID != null && !gym.currentLeaderUUID.equals(target.getStringUUID())) {
            GymData.get().removeLeaderTeam(typeId);
        }

        gym.currentLeader = target.getName().getString();
        gym.currentLeaderUUID = target.getStringUUID();
        gym.leaderStartDate = Instant.now().toString();
        gym.leaderRegistered = false;
        GymConfig.save();

        GymLeaderHistory.logLeaderChange(type.getId(), target.getName().getString(), 
                target.getStringUUID(), "Set by admin");

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + target.getName().getString() + 
                " is now the " + gym.displayName + " Leader").withStyle(ChatFormatting.GREEN), true);

        // Notify new leader
        target.sendSystemMessage(Component.literal("═══════════════════════════════════")
                .withStyle(ChatFormatting.GOLD));
        target.sendSystemMessage(Component.literal("  You have been appointed as the")
                .withStyle(ChatFormatting.YELLOW));
        target.sendSystemMessage(Component.literal("  " + gym.displayName + " Leader!")
                .withStyle(type.getColor(), ChatFormatting.BOLD));
        target.sendSystemMessage(Component.literal(""));
        target.sendSystemMessage(Component.literal("  1. Register your team:")
                .withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(Component.literal("     /eb gym register " + type.getId())
                .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("  2. Set battle rules:")
                .withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(Component.literal("     /eb gym rules " + type.getId() + " set <format> <levelcap>")
                .withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("═══════════════════════════════════")
                .withStyle(ChatFormatting.GOLD));

        return 1;
    }

    private static int removeLeader(CommandContext<CommandSourceStack> ctx, String reason) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null || gym.currentLeader == null) {
            ctx.getSource().sendFailure(Component.literal("This gym has no leader"));
            return 0;
        }

        String oldLeader = gym.currentLeader;
        
        GymLeaderHistory.logLeaderRemoval(type.getId(), oldLeader, gym.currentLeaderUUID, reason);
        GymData.get().removeLeaderTeam(typeId);

        gym.currentLeader = null;
        gym.currentLeaderUUID = null;
        gym.leaderStartDate = null;
        gym.leaderRegistered = false;
        GymConfig.save();

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Removed " + oldLeader + 
                " from " + gym.displayName).withStyle(ChatFormatting.YELLOW), true);

        return 1;
    }

    private static int setRewards(CommandContext<CommandSourceStack> ctx) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        String badge = StringArgumentType.getString(ctx, "badge");
        String tm = StringArgumentType.getString(ctx, "tm");
        int coins = IntegerArgumentType.getInteger(ctx, "coins");
        
        GymType type = GymType.fromId(typeId);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null) {
            ctx.getSource().sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        gym.rewards.badge = badge;
        gym.rewards.tm = tm.equals("none") ? "" : tm;
        gym.rewards.silverCoins = coins;
        GymConfig.save();

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Updated rewards for " + gym.displayName)
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }

    private static int showAdminGymInfo(CommandContext<CommandSourceStack> ctx) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null) {
            ctx.getSource().sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        GymData.LeaderTeamData teamData = GymData.get().getLeaderTeam(typeId);
        CommandSourceStack src = ctx.getSource();

        src.sendSuccess(() -> Component.literal("══════ Admin Info: " + gym.displayName + " ══════")
                .withStyle(type.getColor(), ChatFormatting.BOLD), false);

        src.sendSuccess(() -> Component.literal("  Enabled: " + gym.enabled)
                .withStyle(gym.enabled ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        
        src.sendSuccess(() -> Component.literal("  Leader: " + (gym.currentLeader != null ? gym.currentLeader : "None"))
                .withStyle(ChatFormatting.GRAY), false);
        
        if (gym.currentLeaderUUID != null) {
            src.sendSuccess(() -> Component.literal("  UUID: " + gym.currentLeaderUUID)
                    .withStyle(ChatFormatting.DARK_GRAY), false);
            src.sendSuccess(() -> Component.literal("  Registered: " + gym.leaderRegistered)
                    .withStyle(gym.leaderRegistered ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            
            if (teamData != null) {
                String format = teamData.battleFormat != null ? teamData.battleFormat : "singles";
                int levelCap = teamData.levelCap > 0 ? teamData.levelCap : 50;
                src.sendSuccess(() -> Component.literal("  Format: " + format + " | Level Cap: " + levelCap)
                        .withStyle(ChatFormatting.GRAY), false);
                src.sendSuccess(() -> Component.literal("  Team Size: " + (teamData.team != null ? teamData.team.size() : 0))
                        .withStyle(ChatFormatting.GRAY), false);
            }
            
            if (gym.leaderStartDate != null) {
                try {
                    String formatted = DATE_FMT.format(Instant.parse(gym.leaderStartDate));
                    src.sendSuccess(() -> Component.literal("  Since: " + formatted)
                            .withStyle(ChatFormatting.GRAY), false);
                } catch (Exception ignored) {}
            }
        }

        src.sendSuccess(() -> Component.literal("  Rewards:")
                .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("    Badge: " + gym.rewards.badge)
                .withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("    TM: " + (gym.rewards.tm.isEmpty() ? "None" : gym.rewards.tm))
                .withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.literal("    Coins: " + gym.rewards.silverCoins)
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymConfig.GymEntry gym = GymConfig.get().getGym(type);
        if (gym == null) {
            ctx.getSource().sendFailure(Component.literal("Gym not found"));
            return 0;
        }

        gym.enabled = enabled;
        GymConfig.save();

        ctx.getSource().sendSuccess(() -> Component.literal("✓ " + gym.displayName + " is now " + 
                (enabled ? "enabled" : "disabled")).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);

        return 1;
    }

    private static int resetStats(CommandContext<CommandSourceStack> ctx) {
        String target = StringArgumentType.getString(ctx, "target");
        
        if (target.equalsIgnoreCase("all")) {
            GymData.get().resetAllPlayerStats();
            ctx.getSource().sendSuccess(() -> Component.literal("✓ Reset all player gym stats")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            ServerPlayer player = ctx.getSource().getServer().getPlayerList().getPlayerByName(target);
            if (player != null) {
                GymData.get().resetPlayerStats(player.getStringUUID());
                ctx.getSource().sendSuccess(() -> Component.literal("✓ Reset gym stats for " + target)
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                ctx.getSource().sendFailure(Component.literal("Player not found: " + target + 
                        ". Use 'all' to reset all stats."));
                return 0;
            }
        }
        
        return 1;
    }

    private static int adminResetTeam(CommandContext<CommandSourceStack> ctx) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymData.get().resetLeaderTeam(typeId);

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Reset team for " + type.getDisplayName() + " Gym")
                .withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("  The leader must register their team again.")
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int adminSetRules(CommandContext<CommandSourceStack> ctx) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        String format = StringArgumentType.getString(ctx, "format").toLowerCase();
        int levelCap = IntegerArgumentType.getInteger(ctx, "levelcap");
        
        GymType type = GymType.fromId(typeId);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        if (!format.equals("singles") && !format.equals("doubles")) {
            ctx.getSource().sendFailure(Component.literal("Invalid format. Use 'singles' or 'doubles'"));
            return 0;
        }

        if (levelCap != 50 && levelCap != 100) {
            ctx.getSource().sendFailure(Component.literal("Level cap must be 50 or 100"));
            return 0;
        }

        GymData.get().setGymRules(typeId, format, levelCap);

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Rules set for " + type.getDisplayName() + " Gym")
                .withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("  Format: " + format + " | Level Cap: " + levelCap)
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int adminResetRules(CommandContext<CommandSourceStack> ctx) {
        String typeId = StringArgumentType.getString(ctx, "gymtype");
        GymType type = GymType.fromId(typeId);
        
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown gym type: " + typeId));
            return 0;
        }

        GymData.get().resetGymRules(typeId);

        ctx.getSource().sendSuccess(() -> Component.literal("✓ Reset rules timer for " + type.getDisplayName() + " Gym")
                .withStyle(ChatFormatting.GREEN), true);
        ctx.getSource().sendSuccess(() -> Component.literal("  The leader can now change rules immediately.")
                .withStyle(ChatFormatting.GRAY), false);

        return 1;
    }

    private static int showHistory(CommandContext<CommandSourceStack> ctx, int lines) {
        List<String> history = GymLeaderHistory.getHistory(lines);
        
        ctx.getSource().sendSuccess(() -> Component.literal("══════ Gym Leader History ══════")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        if (history.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  No history entries")
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            for (String entry : history) {
                ctx.getSource().sendSuccess(() -> Component.literal("  " + entry)
                        .withStyle(ChatFormatting.GRAY), false);
            }
        }

        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        GymConfig.reload();
        ctx.getSource().sendSuccess(() -> Component.literal("✓ Gym config reloaded")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
