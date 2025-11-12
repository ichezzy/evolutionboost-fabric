package com.ichezzy.evolutionboost.mixin;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.logging.CommandLogManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hakt sich direkt in Brigadiers Ausführung ein.
 * remap=false, weil Brigadier keine Mojang-Namensgebung hat.
 * Keine generische Mixin-Signatur (vermeidet Target-Mismatches).
 */
@Mixin(value = CommandDispatcher.class, remap = false)
public abstract class CommandManagerMixin {

    // execute(ParseResults<?>)I  – zentraler Pfad
    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;)I", at = @At("TAIL"))
    private void evolutionboost$afterExecuteParse(ParseResults<?> parse, CallbackInfoReturnable<Integer> cir) {
        final int result = cir.getReturnValue();
        final boolean success = result >= 0;

        Object src = parse.getContext().getSource();
        CommandSourceStack css = (src instanceof CommandSourceStack) ? (CommandSourceStack) src
                : CommandLogManager.tryToStack(src);

        final String input = parse.getReader().getString(); // inkl. führendem "/"
        EvolutionBoost.LOGGER.debug("[{}] mixin afterExecute(ParseResults) src={} input='{}' result={}",
                EvolutionBoost.MOD_ID, (css != null ? css.getTextName() : "null"), input, result);

        CommandLogManager.logAfter(css, input, result, success);
    }

    // execute(String, Object)I – zusätzliche Aufrufer
    @Inject(method = "execute(Ljava/lang/String;Ljava/lang/Object;)I", at = @At("TAIL"))
    private void evolutionboost$afterExecuteString(String input, Object source, CallbackInfoReturnable<Integer> cir) {
        final int result = cir.getReturnValue();
        final boolean success = result >= 0;

        CommandSourceStack css = (source instanceof CommandSourceStack) ? (CommandSourceStack) source
                : CommandLogManager.tryToStack(source);

        EvolutionBoost.LOGGER.debug("[{}] mixin afterExecute(String,Object) src={} input='{}' result={}",
                EvolutionBoost.MOD_ID, (css != null ? css.getTextName() : "null"), input, result);

        CommandLogManager.logAfter(css, input, result, success);
    }
}
