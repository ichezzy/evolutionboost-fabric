package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * /eb help - Zeigt alle verfügbaren Commands basierend auf Berechtigungen.
 */
public final class HelpCommand {
    private HelpCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var helpTree = Commands.literal("help")
                .executes(ctx -> showHelp(ctx.getSource(), 1))
                .then(Commands.literal("boost")
                        .executes(ctx -> showBoostHelp(ctx.getSource())))
                .then(Commands.literal("event")
                        .executes(ctx -> showEventHelp(ctx.getSource())))
                .then(Commands.literal("rewards")
                        .executes(ctx -> showRewardsHelp(ctx.getSource())))
                .then(Commands.literal("weather")
                        .executes(ctx -> showWeatherHelp(ctx.getSource())))
                .then(Commands.literal("quest")
                        .executes(ctx -> showQuestHelp(ctx.getSource())));

        d.register(Commands.literal("evolutionboost").then(helpTree));
        d.register(Commands.literal("eb").then(helpTree.build()));
    }

    private static int showHelp(CommandSourceStack src, int page) {
        src.sendSuccess(() -> header("EvolutionBoost Help"), false);
        src.sendSuccess(() -> Component.literal("Use /eb help <topic> for details").withStyle(ChatFormatting.GRAY), false);
        src.sendSuccess(() -> Component.empty(), false);

        // Player Commands (everyone)
        src.sendSuccess(() -> section("Player Commands"), false);
        sendCmd(src, "/eb quest active", "Show your active quests", null);
        sendCmd(src, "/eb quest progress", "Show quest progress", null);
        sendCmd(src, "/eb quest info <id>", "Show quest details", null);

        // Admin - Boost
        if (hasPermission(src, "evolutionboost.boost", 2)) {
            src.sendSuccess(() -> Component.empty(), false);
            src.sendSuccess(() -> section("Boost Commands").append(clickable(" [details]", "/eb help boost", "Click for boost help")), false);
            sendCmd(src, "/eb boost add global <type> <mult> <time>", "Add global boost", "evolutionboost.boost");
            sendCmd(src, "/eb boost add dim <dim> <type> <mult>", "Set dimension boost", "evolutionboost.boost");
            sendCmd(src, "/eb boost clear ...", "Clear boosts", "evolutionboost.boost");
        }

        // Admin - Event
        if (hasPermission(src, "evolutionboost.event", 2)) {
            src.sendSuccess(() -> Component.empty(), false);
            src.sendSuccess(() -> section("Event Commands").append(clickable(" [details]", "/eb help event", "Click for event help")), false);
            sendCmd(src, "/eb event spawn <id> <player>", "Spawn event entity", "evolutionboost.event");
            sendCmd(src, "/eb event npc <action> ...", "Manage event NPCs", "evolutionboost.event");
        }

        // Admin - Rewards
        if (hasPermission(src, "evolutionboost.rewards", 2)) {
            src.sendSuccess(() -> Component.empty(), false);
            src.sendSuccess(() -> section("Reward Commands").append(clickable(" [details]", "/eb help rewards", "Click for rewards help")), false);
            sendCmd(src, "/eb rewards set <player> <type> <value>", "Set reward eligibility", "evolutionboost.rewards");
            sendCmd(src, "/eb rewards list <type>", "List eligible players", "evolutionboost.rewards");
            sendCmd(src, "/eb rewards reload", "Reload reward configs", "evolutionboost.rewards");
        }

        // Admin - Weather
        if (hasPermission(src, "evolutionboost.weather", 2)) {
            src.sendSuccess(() -> Component.empty(), false);
            src.sendSuccess(() -> section("Weather Commands").append(clickable(" [details]", "/eb help weather", "Click for weather help")), false);
            sendCmd(src, "/eb weather christmas storm on/off", "Toggle blizzard", "evolutionboost.weather");
            sendCmd(src, "/eb weather christmas auto on/off", "Toggle auto-cycle", "evolutionboost.weather");
        }

        // Admin - Quest
        if (hasPermission(src, "evolutionboost.quest.admin", 2)) {
            src.sendSuccess(() -> Component.empty(), false);
            src.sendSuccess(() -> section("Quest Admin Commands").append(clickable(" [details]", "/eb help quest", "Click for quest help")), false);
            sendCmd(src, "/eb quest <line> <id> activate <player>", "Activate quest", "evolutionboost.quest.admin");
            sendCmd(src, "/eb quest <line> <id> complete <player>", "Complete quest", "evolutionboost.quest.admin");
        }

        src.sendSuccess(() -> Component.empty(), false);
        src.sendSuccess(() -> footer(), false);

        return 1;
    }

    // ==================== Topic Help ====================

    private static int showBoostHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Boost Commands"), false);

        src.sendSuccess(() -> section("Add Boosts"), false);
        sendCmdDetail(src, "/eb boost add global <type> <mult> <value> <unit>",
                "Add a temporary global boost",
                "Types: SHINY, EV, IV, HA, CATCH",
                "Units: s, m, h, d (seconds, minutes, hours, days)",
                "Example: /eb boost add global SHINY 2.0 30 m");

        sendCmdDetail(src, "/eb boost add dim <dimension> <type> <mult>",
                "Set a permanent dimension multiplier",
                "Example: /eb boost add dim minecraft:the_nether SHINY 1.5");

        src.sendSuccess(() -> section("Clear Boosts"), false);
        sendCmdDetail(src, "/eb boost clear all", "Clear all active global boosts");
        sendCmdDetail(src, "/eb boost clear global [type]", "Clear global boosts (optionally by type)");
        sendCmdDetail(src, "/eb boost clear dim <dimension> [type]", "Clear dimension multipliers");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showEventHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Event Commands"), false);

        src.sendSuccess(() -> section("Event Spawns"), false);
        sendCmdDetail(src, "/eb event spawn <id> <player>",
                "Spawn an event entity for a player",
                "Example: /eb event spawn christmas_boss Steve");

        src.sendSuccess(() -> section("NPC Management"), false);
        sendCmdDetail(src, "/eb event npc create <id>", "Create a new event NPC");
        sendCmdDetail(src, "/eb event npc remove <id>", "Remove an event NPC");
        sendCmdDetail(src, "/eb event npc list", "List all event NPCs");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showRewardsHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Reward Commands"), false);

        src.sendSuccess(() -> section("Eligibility"), false);
        sendCmdDetail(src, "/eb rewards set <player> donator <tier>",
                "Set donator tier for player",
                "Tiers: none, copper, silver, gold",
                "Example: /eb rewards set Steve donator gold");

        sendCmdDetail(src, "/eb rewards set <player> gym <true/false>",
                "Set gym leader status");

        sendCmdDetail(src, "/eb rewards set <player> staff <true/false>",
                "Set staff status");

        src.sendSuccess(() -> section("Information"), false);
        sendCmdDetail(src, "/eb rewards list <type>",
                "List players with eligibility",
                "Types: donator, donator_copper, donator_silver, donator_gold, gym, staff");

        sendCmdDetail(src, "/eb rewards reload", "Reload reward configurations");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showWeatherHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Weather Commands"), false);

        src.sendSuccess(() -> section("Christmas Weather"), false);
        sendCmdDetail(src, "/eb weather christmas storm on",
                "Start a blizzard immediately",
                "Players outside will freeze and take damage");

        sendCmdDetail(src, "/eb weather christmas storm off", "Stop the current blizzard");

        sendCmdDetail(src, "/eb weather christmas auto on",
                "Enable automatic blizzard cycle",
                "Blizzards occur every ~60 minutes");

        sendCmdDetail(src, "/eb weather christmas auto off", "Disable automatic blizzard cycle");

        sendCmdDetail(src, "/eb weather christmas status", "Show current weather status");

        sendCmdDetail(src, "/eb weather christmas init", "Initialize base Christmas boosts");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showQuestHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Quest Commands"), false);

        src.sendSuccess(() -> section("Player Commands"), false);
        sendCmdDetail(src, "/eb quest active", "Show your active quests");
        sendCmdDetail(src, "/eb quest progress", "Show progress for all active quests");
        sendCmdDetail(src, "/eb quest info <questId>", "Show details for a specific quest");

        if (hasPermission(src, "evolutionboost.quest.admin", 2)) {
            src.sendSuccess(() -> section("Admin Commands"), false);
            sendCmdDetail(src, "/eb quest list [questline]", "List all quests or quests in a line");

            sendCmdDetail(src, "/eb quest <line> <id> activate <player>",
                    "Activate a quest for a player",
                    "Checks prerequisites before activating",
                    "Example: /eb quest christmas mq1 activate Steve");

            sendCmdDetail(src, "/eb quest <line> <id> set <player> <status>",
                    "Directly set quest status",
                    "Status: active, available, locked, completed");

            sendCmdDetail(src, "/eb quest <line> <id> complete <player>",
                    "Complete quest and give rewards",
                    "Only works if all objectives are done");

            sendCmdDetail(src, "/eb quest <line> <id> reset <player>", "Reset quest progress");

            sendCmdDetail(src, "/eb quest <line> <id> progress <player> <obj> <amount>",
                    "Manually add progress to an objective");
        }

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    // ==================== Helper Methods ====================

    private static MutableComponent header(String title) {
        return Component.literal("═══════ ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(title).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" ═══════").withStyle(ChatFormatting.GOLD));
    }

    private static MutableComponent section(String title) {
        return Component.literal("▸ " + title).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    private static MutableComponent footer() {
        return Component.literal("════════════════════════════════").withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent clickable(String text, String command, String tooltip) {
        return Component.literal(text)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(tooltip))));
    }

    private static void sendCmd(CommandSourceStack src, String cmd, String desc, String permission) {
        if (permission != null && !hasPermission(src, permission, 2)) return;

        MutableComponent line = Component.literal("  ")
                .append(Component.literal(cmd).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(desc).withStyle(ChatFormatting.GRAY));

        // Make clickable to suggest command
        String baseCmd = cmd.split(" <")[0].split(" \\[")[0];
        line = line.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, baseCmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to use"))));

        final MutableComponent finalLine = line;
        src.sendSuccess(() -> finalLine, false);
    }

    private static void sendCmdDetail(CommandSourceStack src, String cmd, String... descriptions) {
        MutableComponent cmdLine = Component.literal("  ")
                .append(Component.literal(cmd).withStyle(ChatFormatting.GREEN));

        String baseCmd = cmd.split(" <")[0].split(" \\[")[0];
        cmdLine = cmdLine.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, baseCmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to use"))));

        final MutableComponent finalCmdLine = cmdLine;
        src.sendSuccess(() -> finalCmdLine, false);

        for (String desc : descriptions) {
            src.sendSuccess(() -> Component.literal("    " + desc).withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static boolean hasPermission(CommandSourceStack src, String permission, int defaultLevel) {
        return EvolutionboostPermissions.check(src, permission, defaultLevel, false);
    }
}
