package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.permission.EvolutionboostPermissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * /eb help - Shows all available commands with pagination.
 */
public final class HelpCommand {
    private HelpCommand() {}

    private static final int LINES_PER_PAGE = 12;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var helpTree = Commands.literal("help")
                .executes(ctx -> showHelp(ctx.getSource(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> showHelp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "page"))))
                .then(Commands.literal("boost")
                        .executes(ctx -> showBoostHelp(ctx.getSource())))
                .then(Commands.literal("quest")
                        .executes(ctx -> showQuestHelp(ctx.getSource())))
                .then(Commands.literal("dex")
                        .executes(ctx -> showDexHelp(ctx.getSource())))
                .then(Commands.literal("gym")
                        .executes(ctx -> showGymHelp(ctx.getSource())))
                .then(Commands.literal("admin")
                        .executes(ctx -> showAdminHelp(ctx.getSource())))
                .then(Commands.literal("rewards")
                        .executes(ctx -> showRewardsHelp(ctx.getSource())))
                .then(Commands.literal("weather")
                        .executes(ctx -> showWeatherHelp(ctx.getSource())));

        d.register(Commands.literal("evolutionboost").then(helpTree));
        d.register(Commands.literal("eb").then(helpTree.build()));
    }

    // ==================== Main Help with Pages ====================

    private static int showHelp(CommandSourceStack src, int page) {
        int totalPages = 3;
        final int currentPage = Math.max(1, Math.min(page, totalPages));

        src.sendSuccess(() -> header("EvolutionBoost Help (" + currentPage + "/" + totalPages + ")"), false);

        switch (currentPage) {
            case 1 -> showPage1(src);
            case 2 -> showPage2(src);
            case 3 -> showPage3(src);
        }

        // Navigation
        MutableComponent nav = Component.literal("  ");
        if (currentPage > 1) {
            nav.append(clickable("[< Prev]", "/eb help " + (currentPage - 1), "Previous page"))
                    .append(Component.literal(" ").withStyle(ChatFormatting.GRAY));
        }
        nav.append(Component.literal("Page " + currentPage + "/" + totalPages).withStyle(ChatFormatting.GRAY));
        if (currentPage < totalPages) {
            nav.append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                    .append(clickable("[Next >]", "/eb help " + (currentPage + 1), "Next page"));
        }
        src.sendSuccess(() -> nav, false);
        src.sendSuccess(() -> footer(), false);

        return 1;
    }

    private static void showPage1(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("Use /eb help <topic> for details")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        src.sendSuccess(() -> Component.literal("Topics: boost, quest, dex, gym, admin, rewards, weather")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        src.sendSuccess(() -> Component.empty(), false);

        // Player Commands
        src.sendSuccess(() -> section("Player Commands"), false);
        sendCmd(src, "/eb boost info", "Show all active boosts", null);
        sendCmd(src, "/eb quest daily/weekly/monthly", "View random quests", null);
        sendCmd(src, "/eb quest turnin <type>", "Turn in completed quest", null);
        sendCmd(src, "/eb dex info", "Show Pokédex progress", null);
        sendCmd(src, "/eb dex claim <milestone>", "Claim milestone reward", null);
        sendCmd(src, "/eb hud on/off", "Toggle boost HUD", null);
    }

    private static void showPage2(CommandSourceStack src) {
        // Notification Commands
        src.sendSuccess(() -> section("Notifications"), false);
        sendCmd(src, "/eb notifications", "Show notification settings", null);
        sendCmd(src, "/eb notifications on/off <type>", "Toggle (all/rewards/dex/quests)", null);
        src.sendSuccess(() -> Component.empty(), false);

        // Story Quests
        src.sendSuccess(() -> section("Story Quests"), false);
        sendCmd(src, "/eb quest active", "Show active quests", null);
        sendCmd(src, "/eb quest progress", "Show quest progress", null);
        sendCmd(src, "/eb quest info <id>", "Show quest details", null);
    }

    private static void showPage3(CommandSourceStack src) {
        // Admin Commands (permission gated)
        if (hasPermission(src, "evolutionboost.boost", 2)) {
            src.sendSuccess(() -> section("Admin: Boosts").append(clickable(" [details]", "/eb help boost", "Click")), false);
            sendCmd(src, "/eb boost add global <type> <mult> <time>", "Add global boost", "evolutionboost.boost");
            sendCmd(src, "/eb boost clear all", "Clear all boosts", "evolutionboost.boost");
        }

        if (hasPermission(src, "evolutionboost.admin", 3)) {
            src.sendSuccess(() -> section("Admin: Server").append(clickable(" [details]", "/eb help admin", "Click")), false);
            sendCmd(src, "/eb admin tp <dimension>", "Teleport to dimension", "evolutionboost.admin");
            sendCmd(src, "/eb admin return", "Return to spawn", "evolutionboost.admin");
        }

        if (hasPermission(src, "evolutionboost.quest.admin", 2)) {
            src.sendSuccess(() -> section("Admin: Quests").append(clickable(" [details]", "/eb help quest", "Click")), false);
            sendCmd(src, "/eb quest admin ...", "Quest administration", "evolutionboost.quest.admin");
        }
    }

    // ==================== Topic Help ====================

    private static int showBoostHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Boost Commands"), false);

        src.sendSuccess(() -> section("Player"), false);
        sendCmdDetail(src, "/eb boost info", "Show all active global boosts");

        if (hasPermission(src, "evolutionboost.boost", 2)) {
            src.sendSuccess(() -> section("Admin - Add Boosts"), false);
            sendCmdDetail(src, "/eb boost add global <type> <mult> <value> <unit>",
                    "Add temporary global boost",
                    "Types: SHINY, XP, EV, IV",
                    "Units: s, m, h, d",
                    "Example: /eb boost add global SHINY 2.0 1 h");
            sendCmdDetail(src, "/eb boost add dim <dimension> <type> <mult>",
                    "Set permanent dimension multiplier",
                    "Example: /eb boost add dim minecraft:the_nether SHINY 1.5");

            src.sendSuccess(() -> section("Admin - Clear Boosts"), false);
            sendCmdDetail(src, "/eb boost clear all", "Clear ALL active boosts");
            sendCmdDetail(src, "/eb boost clear global [type]", "Clear global boosts");
            sendCmdDetail(src, "/eb boost clear dim <dimension> [type]", "Clear dimension multipliers");
        }

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showQuestHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Quest Commands"), false);

        src.sendSuccess(() -> section("Random Quests"), false);
        sendCmdDetail(src, "/eb quest daily", "View daily quest", "Reward: 5-10 Bronze Coins (base 5 + streak up to +5)");
        sendCmdDetail(src, "/eb quest weekly", "View weekly quest", "Reward: 3 Silver Coins");
        sendCmdDetail(src, "/eb quest monthly", "View monthly quest", "Reward: 1 Gold Coin");
        sendCmdDetail(src, "/eb quest turnin <daily|weekly|monthly>", "Turn in completed quest");

        src.sendSuccess(() -> section("Story Quests"), false);
        sendCmdDetail(src, "/eb quest active", "Show your active quests");
        sendCmdDetail(src, "/eb quest progress", "Show progress on all quests");
        sendCmdDetail(src, "/eb quest info <id>", "Show quest details");

        if (hasPermission(src, "evolutionboost.quest.admin", 2)) {
            src.sendSuccess(() -> section("Admin Commands"), false);
            sendCmdDetail(src, "/eb quest list [questline]", "List all quests");
            sendCmdDetail(src, "/eb quest admin status <player> <line> <quest> <status>",
                    "Set quest status (locked/available/active/ready/completed)");
            sendCmdDetail(src, "/eb quest admin progress <player> <line> <quest> <obj> <amount>",
                    "Add progress to objective");
            sendCmdDetail(src, "/eb quest admin reroll <type> [player]",
                    "Reroll random quests (daily/weekly/monthly/all)");
            sendCmdDetail(src, "/eb quest admin complete <player> <type>",
                    "Force-complete random quest");
        }

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showDexHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Pokédex Commands"), false);

        src.sendSuccess(() -> section("Player Commands"), false);
        sendCmdDetail(src, "/eb dex info", "Show your Pokédex progress and milestones");
        sendCmdDetail(src, "/eb dex list", "List all available milestones");
        sendCmdDetail(src, "/eb dex claim <milestone>", "Claim a milestone reward");
        sendCmdDetail(src, "/eb dex pokemon <milestone> <species> [shiny]",
                "Claim Pokémon reward with perfect IVs",
                "Only base forms allowed (no evolutions)");

        if (hasPermission(src, "evolutionboost.dex.admin", 2)) {
            src.sendSuccess(() -> section("Admin Commands"), false);
            sendCmdDetail(src, "/eb dex check <player>", "Check another player's progress");
            sendCmdDetail(src, "/eb dex reload", "Reload dex_rewards.json");
            sendCmdDetail(src, "/eb dex reset <player> <type>",
                    "Reset claimed rewards (all/pokemon/<milestone_id>)");
        }

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showAdminHelp(CommandSourceStack src) {
        if (!hasPermission(src, "evolutionboost.admin", 3)) {
            src.sendFailure(Component.literal("✗ No permission").withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(() -> header("Admin Commands"), false);

        src.sendSuccess(() -> section("Teleportation"), false);
        sendCmdDetail(src, "/eb admin tp <dimension>", "Teleport to any dimension");
        sendCmdDetail(src, "/eb admin return", "Return to Overworld spawn");
        sendCmdDetail(src, "/eb admin setspawn <target>", "Set event spawn point");
        sendCmdDetail(src, "/eb admin tpspawn <type>", "TP players to spawn (online/offline/all)");

        src.sendSuccess(() -> section("Safari Zone"), false);
        sendCmdDetail(src, "/eb safari return", "Return early from Safari Zone");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showRewardsHelp(CommandSourceStack src) {
        if (!hasPermission(src, "evolutionboost.rewards", 2)) {
            src.sendFailure(Component.literal("✗ No permission").withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(() -> header("Reward Commands"), false);

        src.sendSuccess(() -> section("Eligibility"), false);
        sendCmdDetail(src, "/eb rewards set <player> donator <tier>", "Set donator tier (0-5)");
        sendCmdDetail(src, "/eb rewards set <player> voter <true|false>", "Set voter status");
        sendCmdDetail(src, "/eb rewards set <player> supporter <true|false>", "Set supporter status");

        src.sendSuccess(() -> section("Query"), false);
        sendCmdDetail(src, "/eb rewards list <type>", "List eligible players");
        sendCmdDetail(src, "/eb rewards check <player>", "Check player's eligibility");
        sendCmdDetail(src, "/eb rewards reload", "Reload reward configs");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showWeatherHelp(CommandSourceStack src) {
        if (!hasPermission(src, "evolutionboost.weather", 2)) {
            src.sendFailure(Component.literal("✗ No permission").withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(() -> header("Weather Commands"), false);

        src.sendSuccess(() -> section("Christmas Weather"), false);
        sendCmdDetail(src, "/eb weather christmas enable/disable", "Toggle weather system");
        sendCmdDetail(src, "/eb weather christmas storm on/off", "Toggle blizzard");
        sendCmdDetail(src, "/eb weather christmas auto on/off", "Toggle auto-cycle");

        src.sendSuccess(() -> footer(), false);
        return 1;
    }

    private static int showGymHelp(CommandSourceStack src) {
        src.sendSuccess(() -> header("Gym Commands"), false);

        src.sendSuccess(() -> section("Player Commands"), false);
        sendCmdDetail(src, "/eb gym list", "Show all gyms and their leaders");
        sendCmdDetail(src, "/eb gym info <gymtype>", "Show details for a gym");
        sendCmdDetail(src, "/eb gym challenge <gymtype>", "Challenge a gym leader");
        sendCmdDetail(src, "/eb gym accept", "Accept a challenge (as leader)");
        sendCmdDetail(src, "/eb gym decline", "Decline a challenge (as leader)");
        sendCmdDetail(src, "/eb gym stats [player]", "Show gym battle statistics");
        sendCmdDetail(src, "/eb gym rules <gymtype> info", "View gym battle rules");

        src.sendSuccess(() -> section("Leader Commands"), false);
        sendCmdDetail(src, "/eb gym register <gymtype>", 
                "Register your team as gym leader",
                "Team can be changed once per month");
        sendCmdDetail(src, "/eb gym rules <gymtype> set <format> <levelcap>",
                "Set battle rules for your gym",
                "Format: singles or doubles",
                "Level cap: 50 or 100");

        if (hasPermission(src, "evolutionboost.gym.admin", 2)) {
            src.sendSuccess(() -> section("Admin Commands"), false);
            sendCmdDetail(src, "/eb gym admin setleader <gymtype> <player>", "Appoint a gym leader");
            sendCmdDetail(src, "/eb gym admin removeleader <gymtype> [reason]", "Remove a gym leader");
            sendCmdDetail(src, "/eb gym admin rewards <gymtype> <badge> <tm> <coins>", "Set gym rewards");
            sendCmdDetail(src, "/eb gym admin info <gymtype>", "Show detailed gym info");
            sendCmdDetail(src, "/eb gym admin enable/disable <gymtype>", "Enable/disable a gym");
            sendCmdDetail(src, "/eb gym admin resetstats <player|all>", "Reset player gym stats");
            sendCmdDetail(src, "/eb gym admin resetteam <gymtype>", "Reset leader's registered team");
            sendCmdDetail(src, "/eb gym admin rules <gymtype> set/reset", "Set or reset gym rules");
            sendCmdDetail(src, "/eb gym admin history [lines]", "Show leader history");
            sendCmdDetail(src, "/eb gym admin reload", "Reload gym config");
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
                .withStyle(ChatFormatting.YELLOW)
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
