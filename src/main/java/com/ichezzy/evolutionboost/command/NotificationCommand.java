package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.configs.NotificationConfig;
import com.ichezzy.evolutionboost.configs.NotificationConfig.NotificationType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notification toggle commands.
 * 
 * /eb notifications                    - Show current settings
 * /eb notifications on/off all         - Toggle all notifications
 * /eb notifications on/off rewards     - Toggle reward notifications
 * /eb notifications on/off dex         - Toggle dex notifications
 * /eb notifications on/off quests      - Toggle quest notifications
 */
public final class NotificationCommand {
    private NotificationCommand() {}

    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGEST =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("all", "rewards", "dex", "quests"), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var notificationsTree = Commands.literal("notifications")
                // /eb notifications - show status
                .executes(ctx -> showStatus(ctx.getSource()))

                // /eb notifications on <type>
                .then(Commands.literal("on")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TYPE_SUGGEST)
                                .executes(ctx -> toggle(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type"),
                                        true))))

                // /eb notifications off <type>
                .then(Commands.literal("off")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TYPE_SUGGEST)
                                .executes(ctx -> toggle(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "type"),
                                        false))));

        dispatcher.register(Commands.literal("evolutionboost").then(notificationsTree));
        dispatcher.register(Commands.literal("eb").then(notificationsTree.build()));
    }

    private static int showStatus(CommandSourceStack src) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        UUID uuid = player.getUUID();
        Map<NotificationType, Boolean> settings = NotificationConfig.getAllSettings(uuid);

        src.sendSuccess(() -> Component.literal("🔔 Notification Settings")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        for (NotificationType type : NotificationType.values()) {
            boolean enabled = settings.get(type);
            String status = enabled ? "✓ ON" : "✗ OFF";
            ChatFormatting color = enabled ? ChatFormatting.GREEN : ChatFormatting.RED;

            src.sendSuccess(() -> Component.literal("  " + type.getDescription() + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(status)
                            .withStyle(color)), false);
        }

        src.sendSuccess(() -> Component.literal(""), false);
        src.sendSuccess(() -> Component.literal("Toggle: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/eb notifications on/off <type>")
                        .withStyle(ChatFormatting.YELLOW)), false);

        return 1;
    }

    private static int toggle(CommandSourceStack src, String typeArg, boolean enabled) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }

        UUID uuid = player.getUUID();
        String playerName = player.getGameProfile().getName();

        if ("all".equalsIgnoreCase(typeArg)) {
            NotificationConfig.setAllEnabled(uuid, playerName, enabled);
            String status = enabled ? "enabled" : "disabled";
            src.sendSuccess(() -> Component.literal("✓ All notifications " + status)
                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            return 1;
        }

        NotificationType type = NotificationType.fromId(typeArg);
        if (type == null) {
            src.sendFailure(Component.literal("❌ Unknown type: " + typeArg)
                    .append(Component.literal("\nValid: all, rewards, dex, quests")
                            .withStyle(ChatFormatting.GRAY)));
            return 0;
        }

        NotificationConfig.setEnabled(uuid, playerName, type, enabled);
        String status = enabled ? "enabled" : "disabled";
        src.sendSuccess(() -> Component.literal("✓ " + type.getDescription() + " " + status)
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);

        return 1;
    }
}
