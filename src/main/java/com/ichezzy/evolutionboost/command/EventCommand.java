package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.item.TicketManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Event-Kommandos für EvolutionBoost.
 * 
 * /eb event safari return - Verlässt die Safari Zone vorzeitig
 */
public class EventCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> eventTree = Commands.literal("event")
                // /eb event safari
                .then(Commands.literal("safari")
                        // /eb event safari return
                        .then(Commands.literal("return")
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                        ctx.getSource().sendFailure(Component.literal("This command can only be used by players."));
                                        return 0;
                                    }
                                    return safariReturn(player);
                                })));

        // Unter /eb und /evolutionboost registrieren
        dispatcher.register(Commands.literal("eb").then(eventTree));
        dispatcher.register(Commands.literal("evolutionboost").then(eventTree.build()));
    }

    /**
     * Verlässt die Safari Zone vorzeitig.
     */
    private static int safariReturn(ServerPlayer player) {
        // Prüfen ob Spieler in der Safari Zone ist
        String currentDim = player.level().dimension().location().toString();
        if (!currentDim.equals("event:safari_zone")) {
            player.sendSystemMessage(Component.literal("✗ You are not in the Safari Zone!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Prüfen ob eine aktive Session existiert
        TicketManager.Session session = TicketManager.getSession(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("✗ You don't have an active Safari session!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Prüfen ob die Session wirklich für Safari ist
        if (session.target != TicketManager.Target.SAFARI) {
            player.sendSystemMessage(Component.literal("✗ Your current session is not for the Safari Zone!")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        // Session vorzeitig beenden
        if (TicketManager.endSessionEarly(player)) {
            player.sendSystemMessage(Component.literal("✓ You have left the Safari Zone early.")
                    .withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("  Thanks for visiting!")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        } else {
            player.sendSystemMessage(Component.literal("✗ Failed to end Safari session.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
    }
}
