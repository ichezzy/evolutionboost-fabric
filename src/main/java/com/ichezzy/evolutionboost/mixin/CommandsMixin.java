package com.ichezzy.evolutionboost.mixin;

import com.ichezzy.evolutionboost.logging.CommandLogManager;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin für Command-Logging.
 * Hookt in Commands.performCommand() nach Ausführung.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {

    @Inject(method = "performCommand", at = @At("TAIL"))
    private void evolutionboost$afterPerformCommand(
            ParseResults<CommandSourceStack> parseResults,
            String command,
            CallbackInfo ci
    ) {
        try {
            CommandSourceStack source = parseResults.getContext().getSource();
            String cmd = command.startsWith("/") ? command.substring(1) : command;
            // performCommand gibt void zurück, Ergebnis ist nicht direkt verfügbar
            // Wir loggen mit result=1 (OK) - Fehler werden über Exceptions gehandelt
            CommandLogManager.log(source, cmd, 1);
        } catch (Exception ignored) {}
    }
}
