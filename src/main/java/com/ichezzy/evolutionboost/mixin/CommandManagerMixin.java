package com.ichezzy.evolutionboost.mixin;

import com.ichezzy.evolutionboost.logging.CommandLogManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-seitiger Netzwerkknoten: hier kommen Spieler-Befehle an, bevor sie über Brigadier ausgeführt werden.
 * Zielmethoden (Mojang-Mappings, MC 1.21.1):
 *  - performUnsignedChatCommand(String)V   ← zentrale Eingabe von Chat-Commands (ohne Signaturprüfung)
 *  - handleChatCommand(...)V              ← optionaler zusätzlicher Pfad (Injection ist "soft", require=0)
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CommandManagerMixin {

    @Shadow public ServerPlayer player;

    /**
     * Wird aufgerufen, wenn ein Spieler einen unsignierten Chat-Command sendet (z. B. "/time set day" → "time set day").
     * Die Mojang-Mapping-Signatur ist (Ljava/lang/String;)V.
     */
    @Inject(method = "performUnsignedChatCommand", at = @At("TAIL"))
    private void evolutionboost$afterPerformUnsignedChatCommand(String command, CallbackInfo ci) {
        // Quelle ableiten
        final CommandSourceStack css = (this.player != null) ? this.player.createCommandSourceStack() : null;

        // Wir loggen hier unmittelbar nach dem Empfang beim Server.
        // Exaktes Execution-Resultat ist an dieser Stelle nicht verfügbar → result=1, success=true
        CommandLogManager.log(css, "/" + command, 1, true);
    }

    /**
     * Optionaler zusätzlicher Hook – falls die Server-Implementierung (oder bestimmte Server-Setups)
     * noch über handleChatCommand(...) gehen. Falls die Methode nicht existiert, greift diese Injection einfach nicht.
     */
    @Inject(method = "handleChatCommand", at = @At("TAIL"), require = 0)
    private void evolutionboost$afterHandleChatCommand(CallbackInfo ci) {
        // Kein Body nötig – der Hauptweg ist performUnsignedChatCommand(String).
        // Dieser Fallback bleibt "no-op", wenn die Methode in deiner Laufzeit nicht existiert.
    }
}
