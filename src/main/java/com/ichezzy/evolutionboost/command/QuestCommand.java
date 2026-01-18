package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.ichezzy.evolutionboost.quest.*;
import com.ichezzy.evolutionboost.quest.random.*;
import com.ichezzy.evolutionboost.compat.cobblemon.QuestItemHook;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Quest-Commands - Mit <questline> <quest> Format (kein Doppelpunkt).
 *
 * QUEST STATUS FLOW:
 *   LOCKED → AVAILABLE → ACTIVE → READY_TO_COMPLETE → COMPLETED
 *
 * SPIELER-COMMANDS:
 *   /eb quest                              - Hilfe
 *   /eb quest active                       - Aktive Quests anzeigen
 *   /eb quest available                    - Verfügbare Quests anzeigen
 *   /eb quest progress                     - Fortschritt anzeigen
 *   /eb quest info <questline> <quest>     - Quest-Details
 *   /eb quest start <questline> <quest>    - Quest starten (AVAILABLE → ACTIVE)
 *   /eb quest turnin <questline> <quest>   - Quest abgeben (READY → COMPLETED)
 *
 * RANDOM QUEST COMMANDS:
 *   /eb quest daily                        - Zeigt Daily Quest
 *   /eb quest weekly                       - Zeigt Weekly Quest
 *   /eb quest monthly                      - Zeigt Monthly Quest
 *   /eb quest turnin daily/weekly/monthly  - Gibt Random Quest ab
 *
 * ADMIN-COMMANDS:
 *   /eb quest list [questline]
 *   /eb quest admin start <player> <questline> <quest>
 *   /eb quest admin complete <player> <questline> <quest>
 *   /eb quest admin reset <player> <questline> <quest>
 *   /eb quest admin set <player> <questline> <quest> <status>
 *   /eb quest admin progress <player> <questline> <quest> <obj> <amount>
 *   /eb quest reload
 */
public final class QuestCommand {
    private QuestCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var questTree = Commands.literal("quest")

                // /eb quest - Hilfe
                .executes(ctx -> showQuestHelp(ctx.getSource()))

                // ========== SPIELER-COMMANDS ==========

                // /eb quest active
                .then(Commands.literal("active")
                        .executes(ctx -> showActiveQuests(ctx.getSource())))

                // /eb quest available
                .then(Commands.literal("available")
                        .executes(ctx -> showAvailableQuests(ctx.getSource())))

                // /eb quest progress
                .then(Commands.literal("progress")
                        .executes(ctx -> showProgress(ctx.getSource())))

                // /eb quest info <questline> <quest>
                .then(Commands.literal("info")
                        .then(Commands.argument("questline", StringArgumentType.word())
                                .suggests(QuestCommand::suggestQuestLines)
                                .then(Commands.argument("quest", StringArgumentType.word())
                                        .suggests(QuestCommand::suggestQuestsInLine)
                                        .executes(ctx -> showQuestInfo(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "questline"),
                                                StringArgumentType.getString(ctx, "quest"))))))

                // /eb quest start <questline> <quest> - SPIELER startet verfügbare Quest
                .then(Commands.literal("start")
                        .then(Commands.argument("questline", StringArgumentType.word())
                                .suggests(QuestCommand::suggestAvailableQuestLines)
                                .then(Commands.argument("quest", StringArgumentType.word())
                                        .suggests(QuestCommand::suggestAvailableQuests)
                                        .executes(ctx -> playerStartQuest(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "questline"),
                                                StringArgumentType.getString(ctx, "quest"))))))

                // /eb quest turnin <questline> <quest> - SPIELER gibt Quest ab
                // Oder: /eb quest turnin daily/weekly/monthly - Random Quest abgeben
                .then(Commands.literal("turnin")
                        // Random Quest turnin
                        .then(Commands.literal("daily")
                                .executes(ctx -> turninRandomQuest(ctx.getSource(), RandomQuestPeriod.DAILY)))
                        .then(Commands.literal("weekly")
                                .executes(ctx -> turninRandomQuest(ctx.getSource(), RandomQuestPeriod.WEEKLY)))
                        .then(Commands.literal("monthly")
                                .executes(ctx -> turninRandomQuest(ctx.getSource(), RandomQuestPeriod.MONTHLY)))
                        // Story Quest turnin
                        .then(Commands.argument("questline", StringArgumentType.word())
                                .suggests(QuestCommand::suggestReadyQuestLines)
                                .then(Commands.argument("quest", StringArgumentType.word())
                                        .suggests(QuestCommand::suggestReadyQuests)
                                        .executes(ctx -> playerTurninQuest(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "questline"),
                                                StringArgumentType.getString(ctx, "quest"))))))

                // ========== RANDOM QUEST COMMANDS ==========

                // /eb quest daily
                .then(Commands.literal("daily")
                        .executes(ctx -> showRandomQuest(ctx.getSource(), RandomQuestPeriod.DAILY)))

                // /eb quest weekly
                .then(Commands.literal("weekly")
                        .executes(ctx -> showRandomQuest(ctx.getSource(), RandomQuestPeriod.WEEKLY)))

                // /eb quest monthly
                .then(Commands.literal("monthly")
                        .executes(ctx -> showRandomQuest(ctx.getSource(), RandomQuestPeriod.MONTHLY)))

                // ========== NPC/SERVER-COMMANDS ==========
                // Diese Commands werden von NPCs oder dem Server ausgeführt

                // /eb quest unlock <player> <questline> <quest> - Quest einmalig freischalten
                // Funktioniert nur wenn Quest LOCKED ist, kann nicht mehrfach verwendet werden
                .then(Commands.literal("unlock")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("questline", StringArgumentType.word())
                                        .suggests(QuestCommand::suggestQuestLines)
                                        .then(Commands.argument("quest", StringArgumentType.word())
                                                .suggests(QuestCommand::suggestQuestsInLine)
                                                .executes(ctx -> unlockQuest(ctx))))))

                // ========== ADMIN-COMMANDS ==========

                // /eb quest list [questline]
                .then(Commands.literal("list")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false))
                        .executes(ctx -> listAllQuestLines(ctx.getSource()))
                        .then(Commands.argument("questline", StringArgumentType.word())
                                .suggests(QuestCommand::suggestQuestLines)
                                .executes(ctx -> listQuests(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "questline")))))

                // /eb quest reload
                .then(Commands.literal("reload")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false))
                        .executes(ctx -> reloadQuests(ctx.getSource())))

                // /eb quest admin ...
                .then(Commands.literal("admin")
                        .requires(src -> EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false))

                        // /eb quest admin start <player> <questline> <quest>
                        .then(Commands.literal("start")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("questline", StringArgumentType.word())
                                                .suggests(QuestCommand::suggestQuestLines)
                                                .then(Commands.argument("quest", StringArgumentType.word())
                                                        .suggests(QuestCommand::suggestQuestsInLine)
                                                        .executes(ctx -> adminStartQuest(ctx))))))

                        // /eb quest admin complete <player> <questline> <quest>
                        .then(Commands.literal("complete")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("questline", StringArgumentType.word())
                                                .suggests(QuestCommand::suggestQuestLines)
                                                .then(Commands.argument("quest", StringArgumentType.word())
                                                        .suggests(QuestCommand::suggestQuestsInLine)
                                                        .executes(ctx -> adminCompleteQuest(ctx))))))

                        // /eb quest admin reset <player> <questline> <quest>
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("questline", StringArgumentType.word())
                                                .suggests(QuestCommand::suggestQuestLines)
                                                .then(Commands.argument("quest", StringArgumentType.word())
                                                        .suggests(QuestCommand::suggestQuestsInLine)
                                                        .executes(ctx -> adminResetQuest(ctx))))))

                        // /eb quest admin set <player> <questline> <quest> <status>
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("questline", StringArgumentType.word())
                                                .suggests(QuestCommand::suggestQuestLines)
                                                .then(Commands.argument("quest", StringArgumentType.word())
                                                        .suggests(QuestCommand::suggestQuestsInLine)
                                                        .then(Commands.literal("locked")
                                                                .executes(ctx -> adminSetStatus(ctx, QuestStatus.LOCKED)))
                                                        .then(Commands.literal("available")
                                                                .executes(ctx -> adminSetStatus(ctx, QuestStatus.AVAILABLE)))
                                                        .then(Commands.literal("active")
                                                                .executes(ctx -> adminSetStatus(ctx, QuestStatus.ACTIVE)))
                                                        .then(Commands.literal("ready")
                                                                .executes(ctx -> adminSetStatus(ctx, QuestStatus.READY_TO_COMPLETE)))
                                                        .then(Commands.literal("completed")
                                                                .executes(ctx -> adminSetStatus(ctx, QuestStatus.COMPLETED)))))))

                        // /eb quest admin progress <player> <questline> <quest> <objective> <amount>
                        .then(Commands.literal("progress")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("questline", StringArgumentType.word())
                                                .suggests(QuestCommand::suggestQuestLines)
                                                .then(Commands.argument("quest", StringArgumentType.word())
                                                        .suggests(QuestCommand::suggestQuestsInLine)
                                                        .then(Commands.argument("objective", StringArgumentType.word())
                                                                .suggests(QuestCommand::suggestObjectives)
                                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                                        .executes(ctx -> adminAddProgress(ctx))))))))

                        // ==================== RANDOM QUEST ADMIN ====================

                        // /eb quest admin reroll daily/weekly/monthly/all [player]
                        .then(Commands.literal("reroll")
                                .then(Commands.literal("daily")
                                        .executes(ctx -> adminRerollGlobal(ctx.getSource(), RandomQuestPeriod.DAILY))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> adminRerollPlayer(ctx, RandomQuestPeriod.DAILY))))
                                .then(Commands.literal("weekly")
                                        .executes(ctx -> adminRerollGlobal(ctx.getSource(), RandomQuestPeriod.WEEKLY))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> adminRerollPlayer(ctx, RandomQuestPeriod.WEEKLY))))
                                .then(Commands.literal("monthly")
                                        .executes(ctx -> adminRerollGlobal(ctx.getSource(), RandomQuestPeriod.MONTHLY))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> adminRerollPlayer(ctx, RandomQuestPeriod.MONTHLY))))
                                .then(Commands.literal("all")
                                        .executes(ctx -> adminRerollGlobalAll(ctx.getSource()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> adminRerollPlayerAll(ctx)))))

                        // /eb quest admin complete <player> daily/weekly/monthly
                        .then(Commands.literal("complete")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("daily")
                                                .executes(ctx -> adminCompleteRandomQuest(ctx, RandomQuestPeriod.DAILY)))
                                        .then(Commands.literal("weekly")
                                                .executes(ctx -> adminCompleteRandomQuest(ctx, RandomQuestPeriod.WEEKLY)))
                                        .then(Commands.literal("monthly")
                                                .executes(ctx -> adminCompleteRandomQuest(ctx, RandomQuestPeriod.MONTHLY)))))

                        // /eb quest admin setprogress <player> daily/weekly/monthly <index> <amount>
                        .then(Commands.literal("setprogress")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("daily")
                                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> adminSetRandomProgress(ctx, RandomQuestPeriod.DAILY)))))
                                        .then(Commands.literal("weekly")
                                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> adminSetRandomProgress(ctx, RandomQuestPeriod.WEEKLY)))))
                                        .then(Commands.literal("monthly")
                                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                                .executes(ctx -> adminSetRandomProgress(ctx, RandomQuestPeriod.MONTHLY))))))));

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

    private static CompletableFuture<Suggestions> suggestAvailableQuestLines(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            Set<String> available = QuestManager.get().getAvailableQuests(player);
            Set<String> lines = new HashSet<>();
            for (String questId : available) {
                String[] parts = questId.split(":", 2);
                if (parts.length == 2) {
                    lines.add(parts[0]);
                }
            }
            lines.forEach(builder::suggest);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestAvailableQuests(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                Set<String> available = QuestManager.get().getAvailableQuests(player);
                for (String questId : available) {
                    if (questId.startsWith(questLine + ":")) {
                        String[] parts = questId.split(":", 2);
                        if (parts.length == 2) {
                            builder.suggest(parts[1]);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestReadyQuestLines(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            PlayerQuestData data = QuestManager.get().getPlayerData(player);
            for (String questId : data.getQuestsByStatus(QuestStatus.READY_TO_COMPLETE)) {
                String[] parts = questId.split(":", 2);
                if (parts.length == 2) {
                    builder.suggest(parts[0]);
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestReadyQuests(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                PlayerQuestData data = QuestManager.get().getPlayerData(player);
                for (String questId : data.getQuestsByStatus(QuestStatus.READY_TO_COMPLETE)) {
                    if (questId.startsWith(questLine + ":")) {
                        String[] parts = questId.split(":", 2);
                        if (parts.length == 2) {
                            builder.suggest(parts[1]);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestObjectives(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = questLine + ":" + questId;
            QuestManager.get().getQuest(fullId).ifPresent(quest ->
                    quest.getObjectives().forEach(obj -> builder.suggest(obj.getId())));
        } catch (Exception ignored) {}
        return builder.buildFuture();
    }

    // ==================== Helper ====================

    private static String buildFullId(String questLine, String quest) {
        return questLine + ":" + quest;
    }

    // ==================== Help ====================

    private static int showQuestHelp(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("═══════ Quest Commands ═══════")
                .withStyle(ChatFormatting.GOLD), false);

        // Story Quests
        src.sendSuccess(() -> Component.literal("Story Quests:")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), false);

        src.sendSuccess(() -> Component.literal("/eb quest active")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Show active quests").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest available")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Show available quests").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest progress")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Show quest progress").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest info <line> <quest>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Show quest details").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest start <line> <quest>")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" - Start available quest").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest turnin <line> <quest>")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" - Turn in completed quest").withStyle(ChatFormatting.GRAY)), false);

        // Random Quests
        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("Random Quests:")
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), false);

        src.sendSuccess(() -> Component.literal("/eb quest daily")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(" - View daily quest + streak").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest weekly")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" - View weekly quest + streak").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest monthly")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" - View monthly quest").withStyle(ChatFormatting.GRAY)), false);

        src.sendSuccess(() -> Component.literal("/eb quest turnin <daily|weekly|monthly>")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - Turn in").withStyle(ChatFormatting.GRAY)), false);

        if (EvolutionboostPermissions.check(src, "evolutionboost.quest.admin", 2, false)) {
            src.sendSuccess(() -> Component.literal(""), false);
            src.sendSuccess(() -> Component.literal("Admin:")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            src.sendSuccess(() -> Component.literal("/eb quest list [line]")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - List quests").withStyle(ChatFormatting.GRAY)), false);
            src.sendSuccess(() -> Component.literal("/eb quest admin ...")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Admin commands").withStyle(ChatFormatting.GRAY)), false);
            src.sendSuccess(() -> Component.literal("/eb quest admin reroll <daily|weekly|monthly|all> [player]")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(" - Reroll quests").withStyle(ChatFormatting.GRAY)), false);
        }

        src.sendSuccess(() -> Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GOLD), false);

        return 1;
    }

    // ==================== Spieler-Commands ====================

    private static int showActiveQuests(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        QuestItemHook.checkInventoryForQuests(player);
        PlayerQuestData data = QuestManager.get().getPlayerData(player);
        var activeQuests = data.getActiveQuests();

        if (activeQuests.isEmpty()) {
            src.sendSuccess(() -> Component.literal("You have no active quests.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("═══ Active Quests ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (String questId : activeQuests) {
            QuestManager.get().getQuest(questId).ifPresent(quest -> {
                QuestStatus status = data.getStatus(questId);
                ChatFormatting color = status == QuestStatus.READY_TO_COMPLETE
                        ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

                String statusText = status == QuestStatus.READY_TO_COMPLETE
                        ? " [READY TO TURN IN]" : "";

                src.sendSuccess(() -> Component.literal("• " + quest.getName())
                        .withStyle(color)
                        .append(Component.literal(" [" + questId + "]")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(statusText)
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)), false);
            });
        }

        return activeQuests.size();
    }

    private static int showAvailableQuests(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        Set<String> availableQuests = QuestManager.get().getAvailableQuests(player);

        if (availableQuests.isEmpty()) {
            src.sendSuccess(() -> Component.literal("No quests available right now.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("═══ Available Quests ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (String questId : availableQuests) {
            QuestManager.get().getQuest(questId).ifPresent(quest -> {
                src.sendSuccess(() -> Component.literal("• " + quest.getName())
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" [" + questId + "]")
                                .withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(" - /eb quest start " + quest.getQuestLine() + " " + quest.getId())
                                .withStyle(ChatFormatting.GRAY)), false);
            });
        }

        return availableQuests.size();
    }

    private static int showProgress(CommandSourceStack src) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        QuestItemHook.checkInventoryForQuests(player);
        PlayerQuestData data = QuestManager.get().getPlayerData(player);
        var activeQuests = data.getActiveQuests();

        if (activeQuests.isEmpty()) {
            src.sendSuccess(() -> Component.literal("You have no active quests.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        src.sendSuccess(() -> Component.literal("═══ Quest Progress ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (String questId : activeQuests) {
            QuestManager.get().getQuest(questId).ifPresent(quest -> {
                QuestStatus status = data.getStatus(questId);
                ChatFormatting nameColor = status == QuestStatus.READY_TO_COMPLETE
                        ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

                src.sendSuccess(() -> Component.literal("▸ " + quest.getName())
                        .withStyle(nameColor, ChatFormatting.BOLD), false);

                for (QuestObjective obj : quest.getObjectives()) {
                    int progress = data.getObjectiveProgress(questId, obj.getId());
                    boolean complete = progress >= obj.getTarget();

                    String symbol = complete ? "✓" : "○";
                    ChatFormatting color = complete ? ChatFormatting.GREEN : ChatFormatting.GRAY;

                    src.sendSuccess(() -> Component.literal("  " + symbol + " " + obj.getDescription() + ": ")
                            .withStyle(color)
                            .append(Component.literal(progress + "/" + obj.getTarget())
                                    .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.WHITE)), false);
                }

                if (status == QuestStatus.READY_TO_COMPLETE) {
                    src.sendSuccess(() -> Component.literal("  → Use /eb quest turnin " + quest.getQuestLine() + " " + quest.getId())
                            .withStyle(ChatFormatting.GREEN), false);
                }
            });
        }

        return activeQuests.size();
    }

    private static int showQuestInfo(CommandSourceStack src, String questLine, String questId) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        String fullId = buildFullId(questLine, questId);
        var questOpt = QuestManager.get().getQuest(fullId);
        if (questOpt.isEmpty()) {
            src.sendFailure(Component.literal("Unknown quest: " + fullId));
            return 0;
        }

        QuestStatus effectiveStatus = QuestManager.get().getEffectiveStatus(player, fullId);

        // Nur anzeigen wenn nicht LOCKED
        if (effectiveStatus == QuestStatus.LOCKED) {
            src.sendFailure(Component.literal("You don't have access to this quest yet."));
            return 0;
        }

        QuestItemHook.checkInventoryForQuests(player);
        QuestManager.get().showQuestInfo(player, fullId);
        return 1;
    }

    /**
     * SPIELER startet eine verfügbare Quest.
     */
    private static int playerStartQuest(CommandSourceStack src, String questLine, String questId) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        String fullId = buildFullId(questLine, questId);
        var questOpt = QuestManager.get().getQuest(fullId);
        if (questOpt.isEmpty()) {
            src.sendFailure(Component.literal("Unknown quest: " + fullId));
            return 0;
        }

        Quest quest = questOpt.get();
        QuestStatus effectiveStatus = QuestManager.get().getEffectiveStatus(player, fullId);

        // Nur starten wenn AVAILABLE
        if (effectiveStatus != QuestStatus.AVAILABLE) {
            if (effectiveStatus == QuestStatus.ACTIVE || effectiveStatus == QuestStatus.READY_TO_COMPLETE) {
                src.sendFailure(Component.literal("This quest is already active!"));
            } else if (effectiveStatus == QuestStatus.COMPLETED) {
                src.sendFailure(Component.literal("You have already completed this quest."));
            } else {
                src.sendFailure(Component.literal("This quest is not available yet."));
            }
            return 0;
        }

        // Quest aktivieren
        PlayerQuestData data = QuestManager.get().getPlayerData(player);
        data.setStatus(fullId, QuestStatus.ACTIVE);
        QuestManager.get().savePlayerData(player.getUUID());

        // Nachricht
        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("  Quest Started: " + quest.getName())
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  " + quest.getDescription())
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("═══════════════════════════════")
                .withStyle(ChatFormatting.GREEN));

        return 1;
    }

    /**
     * SPIELER gibt eine fertige Quest ab.
     */
    private static int playerTurninQuest(CommandSourceStack src, String questLine, String questId) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        String fullId = buildFullId(questLine, questId);
        var questOpt = QuestManager.get().getQuest(fullId);
        if (questOpt.isEmpty()) {
            src.sendFailure(Component.literal("Unknown quest: " + fullId));
            return 0;
        }

        Quest quest = questOpt.get();
        PlayerQuestData data = QuestManager.get().getPlayerData(player);
        QuestStatus status = data.getStatus(fullId);

        // Prüfe ob Quest READY_TO_COMPLETE ist
        if (status != QuestStatus.READY_TO_COMPLETE) {
            if (status == QuestStatus.ACTIVE) {
                // Prüfe nochmal ob alle Objectives erfüllt sind
                QuestItemHook.checkInventoryForQuests(player);
                status = data.getStatus(fullId);
                
                if (status != QuestStatus.READY_TO_COMPLETE) {
                    src.sendFailure(Component.literal("Quest objectives not complete yet!"));
                    return 0;
                }
            } else if (status == QuestStatus.COMPLETED) {
                src.sendFailure(Component.literal("You have already completed this quest."));
                return 0;
            } else {
                src.sendFailure(Component.literal("This quest is not ready to turn in."));
                return 0;
            }
        }

        // Quest abgeben mit Item-Entfernung
        boolean success = QuestManager.get().completeQuestWithItemRemoval(player, fullId);
        return success ? 1 : 0;
    }

    // ==================== NPC/Server-Commands ====================

    /**
     * Schaltet eine Quest für einen Spieler frei.
     * Funktioniert NUR wenn die Quest LOCKED ist.
     * Kann nicht mehrfach verwendet werden (abuse-sicher).
     */
    private static int unlockQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = buildFullId(questLine, questId);

            var questOpt = QuestManager.get().getQuest(fullId);
            if (questOpt.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("Unknown quest: " + fullId));
                return 0;
            }

            Quest quest = questOpt.get();
            PlayerQuestData data = QuestManager.get().getPlayerData(target);
            QuestStatus currentStatus = data.getStatus(fullId);

            // Nur freischalten wenn LOCKED
            if (currentStatus != QuestStatus.LOCKED) {
                // Quest wurde bereits freigeschaltet/gestartet/abgeschlossen
                // Stille Rückgabe - kein Fehler, aber auch keine Aktion
                // Das verhindert Abuse durch mehrfaches Ausführen
                return 0;
            }

            // Quest auf AVAILABLE setzen
            data.setStatus(fullId, QuestStatus.AVAILABLE);
            QuestManager.get().savePlayerData(target.getUUID());

            // Benachrichtigung an den Spieler
            target.sendSystemMessage(Component.literal(""));
            target.sendSystemMessage(Component.literal("═══ New Quest Available! ═══")
                    .withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.literal("  ★ " + quest.getName())
                    .withStyle(ChatFormatting.AQUA));
            target.sendSystemMessage(Component.literal("  " + quest.getDescription())
                    .withStyle(ChatFormatting.GRAY));
            target.sendSystemMessage(Component.literal("  → /eb quest start " + questLine + " " + questId)
                    .withStyle(ChatFormatting.YELLOW));
            target.sendSystemMessage(Component.literal("═════════════════════════════")
                    .withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.literal(""));

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== Admin-Commands ====================

    private static int listAllQuestLines(CommandSourceStack src) {
        var questLines = QuestManager.get().getQuestLines();

        src.sendSuccess(() -> Component.literal("═══ Quest Lines ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (String line : questLines) {
            var quests = QuestManager.get().getQuestLine(line);
            src.sendSuccess(() -> Component.literal("• " + line)
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" (" + quests.size() + " quests)")
                            .withStyle(ChatFormatting.GRAY)), false);
        }

        return questLines.size();
    }

    private static int listQuests(CommandSourceStack src, String questLine) {
        var quests = QuestManager.get().getQuestLine(questLine);

        if (quests.isEmpty()) {
            src.sendFailure(Component.literal("Unknown quest line or no quests: " + questLine));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("═══ Quests in '" + questLine + "' ═══")
                .withStyle(ChatFormatting.GOLD), false);

        for (Quest quest : quests) {
            src.sendSuccess(() -> Component.literal("• " + quest.getId() + " - " + quest.getName())
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" [" + quest.getCategory() + "]")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);
        }

        return quests.size();
    }

    private static int adminStartQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = buildFullId(questLine, questId);

            boolean success = QuestManager.get().activateQuest(target, fullId);

            if (success) {
                ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Started " + fullId +
                                " for " + target.getName().getString())
                        .withStyle(ChatFormatting.GREEN), false);
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminCompleteQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = buildFullId(questLine, questId);

            // Admin-Complete: überspringt Objective-Prüfung und entfernt keine Items
            boolean success = QuestManager.get().forceCompleteQuest(target, fullId);

            if (success) {
                ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Force-completed " + fullId +
                                " for " + target.getName().getString())
                        .withStyle(ChatFormatting.GREEN), false);
            }

            return success ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminResetQuest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = buildFullId(questLine, questId);

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

    private static int adminSetStatus(CommandContext<CommandSourceStack> ctx, QuestStatus status) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = buildFullId(questLine, questId);

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

    private static int adminAddProgress(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String questLine = StringArgumentType.getString(ctx, "questline");
            String questId = StringArgumentType.getString(ctx, "quest");
            String fullId = buildFullId(questLine, questId);
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
                    data.setStatus(fullId, QuestStatus.READY_TO_COMPLETE);
                    QuestManager.get().savePlayerData(target.getUUID());
                    target.sendSystemMessage(Component.literal("[Quest] Quest ready to turn in!")
                            .withStyle(ChatFormatting.GOLD));
                }
            });

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int reloadQuests(CommandSourceStack src) {
        RandomQuestConfig.reload();
        src.sendSuccess(() -> Component.literal("[Quest] Random quest config reloaded")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    // ==================== Random Quest Commands ====================

    private static int showRandomQuest(CommandSourceStack src, RandomQuestPeriod period) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }

        RandomQuestManager manager = RandomQuestManager.get();
        RandomQuestManager.GeneratedQuest quest = manager.getQuest(period);
        RandomQuestManager.QuestProgress progress = manager.getProgress(player.getUUID(), period);
        RandomQuestManager.StreakData streak = manager.getStreak(player.getUUID(), period);

        // Header
        src.sendSuccess(() -> Component.literal("═══════ ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(period.getDisplayName() + " Quest")
                        .withStyle(period.getColor(), ChatFormatting.BOLD))
                .append(Component.literal(" ═══════")
                        .withStyle(ChatFormatting.GOLD)), false);

        // Streak Info (nur für Perioden mit Streak)
        if (period.hasStreak()) {
            src.sendSuccess(() -> Component.literal("Streak: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(streak.currentStreak + "/" + period.getMaxStreak())
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" → Reward: ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal((streak.currentStreak + 1) + "x ")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(getCoinName(period))
                            .withStyle(period.getColor())), false);
        } else {
            src.sendSuccess(() -> Component.literal("Reward: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(period.getBaseCoinAmount() + "x ")
                            .withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(getCoinName(period))
                            .withStyle(period.getColor())), false);
        }

        src.sendSuccess(() -> Component.empty(), false);

        // Objectives
        boolean allComplete = true;
        for (int i = 0; i < quest.objectives.size(); i++) {
            RandomQuestManager.GeneratedObjective obj = quest.objectives.get(i);
            int current = progress.objectiveProgress[i];
            int target = obj.amount;
            boolean complete = current >= target;
            if (!complete) allComplete = false;

            final int idx = i;
            src.sendSuccess(() -> {
                ChatFormatting statusColor = complete ? ChatFormatting.GREEN : ChatFormatting.WHITE;
                String statusIcon = complete ? "✓" : "○";

                return Component.literal("  " + statusIcon + " ")
                        .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.GRAY)
                        .append(Component.literal(obj.getDescription())
                                .withStyle(statusColor))
                        .append(Component.literal(" [" + current + "/" + target + "]")
                                .withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            }, false);
        }

        src.sendSuccess(() -> Component.empty(), false);

        // Status
        if (progress.completed) {
            src.sendSuccess(() -> Component.literal("✓ Completed!")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        } else if (allComplete) {
            src.sendSuccess(() -> Component.literal("✦ Ready to turn in!")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
            src.sendSuccess(() -> Component.literal("  Use /eb quest turnin " + period.getId())
                    .withStyle(ChatFormatting.GRAY), false);
        } else {
            src.sendSuccess(() -> Component.literal("In Progress...")
                    .withStyle(ChatFormatting.YELLOW), false);
        }

        src.sendSuccess(() -> Component.literal("════════════════════════════")
                .withStyle(ChatFormatting.GOLD), false);

        return 1;
    }

    private static int turninRandomQuest(CommandSourceStack src, RandomQuestPeriod period) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("This command requires a player."));
            return 0;
        }

        RandomQuestManager manager = RandomQuestManager.get();

        if (!manager.isQuestComplete(player.getUUID(), period)) {
            src.sendFailure(Component.literal("Quest not complete! Check /eb quest " + period.getId()));
            return 0;
        }

        RandomQuestManager.QuestProgress progress = manager.getProgress(player.getUUID(), period);
        if (progress.completed) {
            src.sendFailure(Component.literal("You already turned in this " + period.getDisplayName() + " quest!"));
            return 0;
        }

        if (manager.turnInQuest(player, period)) {
            return 1;
        } else {
            src.sendFailure(Component.literal("Failed to turn in quest."));
            return 0;
        }
    }

    private static String getCoinName(RandomQuestPeriod period) {
        return switch (period) {
            case DAILY -> "Bronze Coin";
            case WEEKLY -> "Silver Coin";
            case MONTHLY -> "Gold Coin";
        };
    }

    // ==================== Admin Reroll Commands ====================

    private static int adminRerollGlobal(CommandSourceStack src, RandomQuestPeriod period) {
        RandomQuestManager.get().forceGlobalReroll(period);
        
        src.sendSuccess(() -> Component.literal("[Quest] Rerolled " + period.getDisplayName() + " quest for ALL players")
                .withStyle(ChatFormatting.GREEN), true);
        
        // Benachrichtige alle Spieler
        RandomQuestManager.get().notifyNewQuests(period);
        
        return 1;
    }

    private static int adminRerollGlobalAll(CommandSourceStack src) {
        RandomQuestManager.get().forceGlobalRerollAll();
        
        src.sendSuccess(() -> Component.literal("[Quest] Rerolled ALL random quests for ALL players")
                .withStyle(ChatFormatting.GREEN), true);
        
        // Benachrichtige alle Spieler für alle Perioden
        for (RandomQuestPeriod period : RandomQuestPeriod.values()) {
            RandomQuestManager.get().notifyNewQuests(period);
        }
        
        return 1;
    }

    private static int adminRerollPlayer(CommandContext<CommandSourceStack> ctx, RandomQuestPeriod period) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            RandomQuestManager.get().resetPlayerQuest(target.getUUID(), period);
            
            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Reset " + period.getDisplayName() + 
                    " quest for " + target.getName().getString())
                    .withStyle(ChatFormatting.GREEN), true);
            
            // Benachrichtige den Spieler
            target.sendSystemMessage(Component.literal("✦ Your " + period.getDisplayName() + " Quest has been reset!")
                    .withStyle(period.getColor()));
            target.sendSystemMessage(Component.literal("  Use /eb quest " + period.getId() + " to view the new quest")
                    .withStyle(ChatFormatting.GRAY));
            
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminRerollPlayerAll(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            RandomQuestManager.get().resetPlayerAllQuests(target.getUUID());
            
            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Reset ALL random quests for " + 
                    target.getName().getString())
                    .withStyle(ChatFormatting.GREEN), true);
            
            // Benachrichtige den Spieler
            target.sendSystemMessage(Component.literal("✦ All your Random Quests have been reset!")
                    .withStyle(ChatFormatting.GOLD));
            target.sendSystemMessage(Component.literal("  Use /eb quest daily/weekly/monthly to view")
                    .withStyle(ChatFormatting.GRAY));
            
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminCompleteRandomQuest(CommandContext<CommandSourceStack> ctx, RandomQuestPeriod period) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            RandomQuestManager manager = RandomQuestManager.get();

            // Alle Objectives auf Maximum setzen
            manager.forceCompleteQuest(target, period);

            ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Force-completed " + period.getDisplayName() + 
                    " quest for " + target.getName().getString())
                    .withStyle(ChatFormatting.GREEN), true);

            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int adminSetRandomProgress(CommandContext<CommandSourceStack> ctx, RandomQuestPeriod period) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            int index = IntegerArgumentType.getInteger(ctx, "index");
            int amount = IntegerArgumentType.getInteger(ctx, "amount");

            RandomQuestManager manager = RandomQuestManager.get();
            boolean success = manager.setObjectiveProgress(target.getUUID(), period, index, amount);

            if (success) {
                ctx.getSource().sendSuccess(() -> Component.literal("[Quest] Set " + period.getDisplayName() + 
                        " objective #" + index + " to " + amount + " for " + target.getName().getString())
                        .withStyle(ChatFormatting.GREEN), true);
                return 1;
            } else {
                ctx.getSource().sendFailure(Component.literal("Invalid objective index or quest not found"));
                return 0;
            }
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
