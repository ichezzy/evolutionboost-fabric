package com.ichezzy.evolutionboost.command;

import com.ichezzy.evolutionboost.hud.HudTogglePayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-seitiger HUD Command.
 * Sendet Packets an den Client um die HUD-Einstellung zu ändern.
 * 
 * /eb hud - Zeigt aktuellen Status (Client entscheidet)
 * /eb hud on - Aktiviert HUD
 * /eb hud off - Deaktiviert HUD
 */
public class HudCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> hudTree = Commands.literal("hud")
                // /eb hud - Status abfragen
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        ServerPlayNetworking.send(player, new HudTogglePayload(HudTogglePayload.ACTION_STATUS));
                    }
                    return 1;
                })
                // /eb hud on
                .then(Commands.literal("on")
                        .executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                ServerPlayNetworking.send(player, new HudTogglePayload(HudTogglePayload.ACTION_ON));
                            }
                            return 1;
                        }))
                // /eb hud off
                .then(Commands.literal("off")
                        .executes(ctx -> {
                            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                                ServerPlayNetworking.send(player, new HudTogglePayload(HudTogglePayload.ACTION_OFF));
                            }
                            return 1;
                        }));

        // Unter /eb und /evolutionboost registrieren
        dispatcher.register(Commands.literal("eb").then(hudTree));
        dispatcher.register(Commands.literal("evolutionboost").then(hudTree.build()));
    }
}
