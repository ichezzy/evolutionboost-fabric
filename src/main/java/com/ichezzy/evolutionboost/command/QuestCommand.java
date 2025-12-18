package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.quest.*;
import com.ichezzy.evolutionboost.quest.hooks.QuestItemHook;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

/**
 * Commands für das Quest-System.
 *
 * Spieler-Commands:
 *   /eb quest active              - Zeigt aktive Quests
 *   /eb quest info <quest>        - Zeigt Quest-Details
 *   /eb quest progress            - Zeigt Fortschritt
 *
 * Admin-Commands:
 *   /eb quest <questline> list                           - Liste alle Quests einer Line
 *   /eb quest <questline> <quest> set <player> <status>  - Setzt Quest-Status
 *   /eb quest <questline> <quest> reset <player>         - Reset Quest
 *   /eb quest <questline> <quest> complete <player>      - Komplettiert Quest
 *   /eb quest <questline> <quest> progress <player> <objective> <amount> - Setzt Fortschritt
 *   /eb quest reload                                     - Reload Quest-Definitionen
 */
public final class QuestCommand {
    private QuestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var questTree = Commands.literal("quest")

                // ========== SPIELER-COMMANDS ==========

                // /eb quest active - Zeigt aktive Quests
                .then(Commands.literal("active")
                        .executes(ctx -> showActiveQuests(ctx.getSource())))

                // /eb quest progress - Zeigt Fortschritt aller aktiven Quests
                .then(Commands.literal("progress")
                        .executes(ctx -> showProgress(ctx.getSource())))

                // /eb quest info <questId> - Zeigt Quest-Details
                .then(Commands.literal("info")
                        .then(Commands.argument("questId", StringArgumentType.string())
                                .suggests(QuestCommand::suggestAllQuests)
                                .executes(ctx -> showQuestInfo(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "questId")))))

                // ========== ADMIN-COMMANDS ==========

                // /eb quest reload - Reload
                .then(Commands.literal("reload")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false))
                        .executes(ctx -> reloadQuests(ctx.getSource())))

                // /eb quest list [questline] - Liste Quests
                .then(Commands.literal("list")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false))
                        .executes(ctx -> listAllQuestLines(ctx.getSource()))
                        .then(Commands.argument("questline", StringArgumentType.word())
                                .suggests(QuestCommand::suggestQuestLines)
                                .executes(ctx -> listQuests(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "questline")))))

                // /eb quest <questline> <quest> ...
                .then(Commands.argument("questline", StringArgumentType.word())
                        .suggests(QuestCommand::suggestQuestLines)
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false))
                        .then(Commands.argument("questId", StringArgumentType.word())
                                .suggests(QuestCommand::suggestQuestsInLine)

                                // set <player> <status>
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.literal("active")
                                                        .executes(ctx -> setQuestStatus(ctx, QuestStatus.ACTIVE)))
                                                .then(Commands.literal("available")
                                                        .executes(ctx -> setQuestStatus(ctx, QuestStatus.AVAILABLE)))
                                                .then(Commands.literal("locked")
                                                        .executes(ctx -> setQuestStatus(ctx, QuestStatus.LOCKED)))
                                                .then(Commands.literal("completed")
                                                        .executes(ctx -> setQuestStatus(ctx, QuestStatus.COMPLETED)))))

                                // activate <player> - Aktiviert Quest mit Prereq-Check
                                .then(Commands.literal("activate")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> activateQuest(ctx))))

                                // complete <player> - Komplettiert Quest mit Rewards
                                .then(Commands.literal("complete")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> completeQuest(ctx))))

                                // reset <player>
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> resetQuest(ctx))))

                                // progress <player> <objective> <amount>
                                .then(Commands.literal("progress")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .then(Commands.argument("objective", StringArgumentType.word())
                                                        .suggests(QuestCommand::suggestObjectives)
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                .executes(ctx -> addProgress(ctx))))))

                                // info <player>
                                .then(Commands.literal("info")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> showQuestInfoForPlayer(ctx))))
                        ));

        // Registrieren unter /evolutionboost und /eb
        dispatcher.register(Commands.literal("evolutionboost").then(questTree));
        dispatcher.register(Commands.literal("eb").then(questTree.build()));
    }

    // ==================== Suggestions ====================

    private static CompletableFuture<Suggestions> suggestQuestLines(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        QuestManager.get().getQuestLines().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestQuestsInLine(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            QuestManager.get().getQuestLine(questLine).forEach(q -> builder.suggest(q.getId()));
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAllQuests(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        QuestManager.get().getAllQuests().forEach(q -> builder.suggest(q.getFullId()));
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestObjectives(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;

            QuestManager.get().getQuest(fullId).ifPresent(quest ->
                    quest.getObjectives().forEach(obj -> builder.suggest(obj.getId())));
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    // ==================== Spieler-Commands ====================

    private static int showActiveQuests(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerQuestData data = QuestManager.get().getPlayerData(player);

            var activeQuests = data.getActiveQuests();
            if (activeQuests.isEmpty()) {
                player.sendSystemMessage(Component.literal("You have no active quests.")
                        .withStyle(ChatFormatting.GRAY));
                return 0;
            }

            player.sendSystemMessage(Component.literal("═══ Active Quests ═══")
                    .withStyle(ChatFormatting.GOLD));

            for (String questId : activeQuests) {
                QuestManager.get().getQuest(questId).ifPresent(quest -> {
                    QuestStatus status = data.getStatus(questId);
                    ChatFormatting color = status == QuestStatus.READY_TO_COMPLETE
                            ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

                    player.sendSystemMessage(Component.literal("• " + quest.getName())
                            .withStyle(color)
                            .append(Component.literal(" [" + questId + "]")
                                    .withStyle(ChatFormatting.DARK_GRAY)));
                });
            }

            return activeQuests.size();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showProgress(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();

            // Erst Items im Inventar prüfen
            QuestItemHook.checkInventoryForQuests(player);

            PlayerQuestData data = QuestManager.get().getPlayerData(player);
            var activeQuests = data.getActiveQuests();

            if (activeQuests.isEmpty()) {
                player.sendSystemMessage(Component.literal("You have no active quests.")
                        .withStyle(ChatFormatting.GRAY));
                return 0;
            }

            for (String questId : activeQuests) {
                QuestManager.get().showQuestInfo(player, questId);
            }

            return activeQuests.size();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showQuestInfo(CommandSourceStack src, String questId) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            QuestManager.get().showQuestInfo(player, questId);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Admin-Commands ====================

    private static int reloadQuests(CommandSourceStack src) {
        // TODO: Implementiere Quest-Reload aus Dateien
        src.sendSuccess(() -> Component.literal("[Quest] Reload not yet implemented - quests are hardcoded.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int listAllQuestLines(CommandSourceStack src) {
        var lines = QuestManager.get().getQuestLines();

        src.sendSuccess(() -> Component.literal("═══ Quest Lines ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (String line : lines) {
            int count = QuestManager.get().getQuestLine(line).size();
            src.sendSuccess(() -> Component.literal("• " + line + " (" + count + " quests)")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        return lines.size();
    }

    private static int listQuests(CommandSourceStack src, String questLine) {
        var quests = QuestManager.get().getQuestLine(questLine);

        if (quests.isEmpty()) {
            src.sendFailure(Component.literal("Unknown quest line: " + questLine));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("═══ " + questLine + " Quests ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (Quest quest : quests) {
            src.sendSuccess(() -> Component.literal("• " + quest.getId() + " - " + quest.getName())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" [" + quest.getCategory() + "]")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }

        return quests.size();
    }

    private static int setQuestStatus(CommandContext<CommandSourceStack> ctx, QuestStatus status) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            if (QuestManager.get().getQuest(fullId).isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("Unknown quest: " + fullId));
                return 0;
            }

            PlayerQuestData data = QuestManager.get().getPlayerData(target);
            data.setStatus(fullId, status);
            QuestManager.get().savePlayerData(target.getUUID());

            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Set " + target.getName().getString() +
                            "'s status for " + fullId + " to " + status.name())
                    .withStyle(ChatFormatting.GREEN), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int activateQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            boolean success = QuestManager.get().activateQuest(target, fullId);

            if (success) {
                ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Activated " + fullId +
                                " for " + target.getName().getString())
                        .withStyle(ChatFormatting.GREEN), false);
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int completeQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            boolean success = QuestManager.get().completeQuest(target, fullId);

            if (success) {
                ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Completed " + fullId +
                                " for " + target.getName().getString())
                        .withStyle(ChatFormatting.GREEN), false);
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int resetQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            QuestManager.get().resetQuest(target, fullId);

            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Reset " + fullId +
                            " for " + target.getName().getString())
                    .withStyle(ChatFormatting.YELLOW), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int addProgress(CommandContext<CommandSourceStack> ctx) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String objectiveId = StringArgumentType.getString(ctx, "objective");
            int amount = IntegerArgumentType.getInteger(ctx, "amount");

            PlayerQuestData data = QuestManager.get().getPlayerData(target);

            if (!data.isActive(fullId)) {
                ctx.getSource().sendFailure(Component.literal("Quest is not active for this player."));
                return 0;
            }

            int newProgress = data.incrementObjectiveProgress(fullId, objectiveId, amount);
            QuestManager.get().savePlayerData(target.getUUID());

            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Added " + amount +
                            " progress to " + objectiveId + " (now: " + newProgress + ")")
                    .withStyle(ChatFormatting.GREEN), false);

            // Prüfe ob Quest jetzt komplett
            QuestManager.get().getQuest(fullId).ifPresent(quest -> {
                if (QuestManager.get().areAllObjectivesComplete(quest, data, fullId)) {
                    target.sendSystemMessage(Component.literal("[Quest] Quest ready to complete!")
                            .withStyle(ChatFormatting.GOLD));
                }
            });

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int showQuestInfoForPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "questId");
            String fullId = questLine + ":" + questId;
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");

            QuestManager.get().showQuestInfo(target, fullId);

            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Showed quest info to " +
                    target.getName().getString()).withStyle(ChatFormatting.GREEN), false);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
