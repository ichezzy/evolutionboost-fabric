package com.ichezzy.evolutionboost.mixin;

import com.ichezzy.evolutionboost.logging.CommandLogManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CommandDispatcher.class, remap = false)
public abstract class CommandManagerMixin<S> {

    // execute(ParseResults<S>)I
    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;)I", at = @At("TAIL"))
    private void evolutionboost$afterExecuteParse(ParseResults<S> parse, CallbackInfoReturnable<Integer> cir) {
        final int result = cir.getReturnValue();
        final boolean success = result >= 0;

        S src = parse.getContext().getSource();
        CommandSourceStack css = null;
        if (src instanceof CommandSourceStack) {
            css = (CommandSourceStack) src;
        } else {
            // Fallback (sollte praktisch nicht nötig sein, aber sicher ist sicher)
            css = CommandLogManager.tryToStack(src);
        }

        final String input = parse.getReader().getString(); // vollständiger String inkl. führendem "/"
        CommandLogManager.logAfter(css, input, result, success);
    }

    // execute(String, S)I
    @Inject(method = "execute(Ljava/lang/String;Ljava/lang/Object;)I", at = @At("TAIL"))
    private void evolutionboost$afterExecuteString(String input, Object source, CallbackInfoReturnable<Integer> cir) {
        final int result = cir.getReturnValue();
        final boolean success = result >= 0;

        CommandSourceStack css = null;
        if (source instanceof CommandSourceStack) {
            css = (CommandSourceStack) source;
        } else {
            css = CommandLogManager.tryToStack(source);
        }

        CommandLogManager.logAfter(css, input, result, success);
    }
}
