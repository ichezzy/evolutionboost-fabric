package com.ichezzy.evolutionboost.mixin;

import com.ichezzy.evolutionboost.logging.CommandLogManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.ParseResults;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hookt zentral Brigadiers CommandDispatcher (stabil für Mojang-Mappings).
 * Wir greifen beide execute-Overloads ab und loggen davor/danach.
 *
 * WICHTIG:
 * - value statt targets -> vermeidet die Warnung bei public Klassen.
 * - remap=false -> Brigadier ist eine externe Library, nicht remappen.
 */
@Mixin(value = CommandDispatcher.class, remap = false)
public abstract class CommandManagerMixin {

    // execute(String input, S source) -> int
    @Inject(method = "execute(Ljava/lang/String;Ljava/lang/Object;)I", at = @At("HEAD"))
    private void evo$headExecuteString(String input, Object source, CallbackInfoReturnable<Integer> cir) {
        CommandLogManager.logBefore(CommandLogManager.tryToStack(source), input);
    }

    @Inject(method = "execute(Ljava/lang/String;Ljava/lang/Object;)I", at = @At("RETURN"))
    private void evo$tailExecuteString(String input, Object source, CallbackInfoReturnable<Integer> cir) {
        boolean success = cir.getReturnValue() > 0;
        CommandLogManager.logAfter(CommandLogManager.tryToStack(source), input, cir.getReturnValue(), success);
    }

    // execute(ParseResults<S> parseResults) -> int
    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;)I", at = @At("HEAD"))
    private void evo$headExecuteParsed(ParseResults<?> results, CallbackInfoReturnable<Integer> cir) {
        ImmutableStringReader reader = results.getReader(); // in Brigadier 1.21-Umfeld ist das ImmutableStringReader
        String input = reader != null ? reader.getString() : "";
        Object src = results.getContext() != null ? results.getContext().getSource() : null;
        CommandLogManager.logBefore(CommandLogManager.tryToStack(src), input);
    }

    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;)I", at = @At("RETURN"))
    private void evo$tailExecuteParsed(ParseResults<?> results, CallbackInfoReturnable<Integer> cir) {
        ImmutableStringReader reader = results.getReader();
        String input = reader != null ? reader.getString() : "";
        Object src = results.getContext() != null ? results.getContext().getSource() : null;
        boolean success = cir.getReturnValue() > 0;
        CommandLogManager.logAfter(CommandLogManager.tryToStack(src), input, cir.getReturnValue(), success);
    }
}
