package com.ichezzy.evolutionboost.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin um Fall Damage in bestimmten Dimensionen zu deaktivieren.
 * Betrifft Dimensionen die mit "evolutionboost:" oder "event:" starten.
 */
@Mixin(LivingEntity.class)
public abstract class FallDamageMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void evolutionboost$cancelFallDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        
        // Nur für Spieler
        if (!(self instanceof ServerPlayer player)) {
            return;
        }
        
        // Nur für Fall Damage
        if (!source.is(DamageTypes.FALL)) {
            return;
        }
        
        // Dimension prüfen
        String dimensionKey = player.level().dimension().location().toString();
        
        if (dimensionKey.startsWith("evolutionboost:") || dimensionKey.startsWith("event:")) {
            // Fall Damage in EvolutionBoost/Event Dimensionen canceln
            cir.setReturnValue(false);
        }
    }
}
