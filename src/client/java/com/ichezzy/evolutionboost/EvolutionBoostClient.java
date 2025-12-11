package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.hud.DimBoostHudPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Client-Seite:
 * - empfängt Dim-Boost-Multiplikatoren vom Server (S2C Payload)
 * - zeigt links oben ein HUD nur dann an, wenn in der aktuellen Dimension
 *   ein Dim-Boost > 1.0 aktiv ist.
 */
public final class EvolutionBoostClient implements ClientModInitializer {

    /** Pro Typ: Dimensionaler Multiplikator (aktuelle Dimension). */
    private static final EnumMap<BoostType, Double> DIM_MULTS = new EnumMap<>(BoostType.class);

    @Override
    public void onInitializeClient() {
        // Defaultwerte
        for (BoostType t : BoostType.values()) {
            DIM_MULTS.put(t, 1.0D);
        }

        // --- Netzwerk-Receiver: nur Dim-Multiplikatoren ---
        ClientPlayNetworking.registerGlobalReceiver(
                DimBoostHudPayload.TYPE,
                (payload, context) -> {
                    double[] dims = payload.multipliers();
                    Minecraft client = context.client();

                    // sicherheitshalber auf den Render-Thread schieben
                    client.execute(() -> {
                        int len = Math.min(BoostType.values().length, dims.length);
                        for (int i = 0; i < len; i++) {
                            DIM_MULTS.put(BoostType.values()[i], dims[i]);
                        }
                    });
                }
        );

        // --- HUD links oben rendern ---
        HudRenderCallback.EVENT.register((GuiGraphics graphics, DeltaTracker deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            int x = 4;
            int y = 4;

            for (BoostType type : BoostType.values()) {
                double dim = DIM_MULTS.getOrDefault(type, 1.0D);
                if (dim <= 1.0001D) continue; // nur zeigen, wenn wirklich >1

                String label = switch (type) {
                    case SHINY -> "Shiny";
                    case XP    -> "XP";
                    case DROP  -> "Drop";
                    case IV    -> "IV";
                };

                String text = String.format(Locale.ROOT, "[Boost] %s x%.2f (Dim)", label, dim);

                int color = switch (type) {
                    case SHINY -> 0xFFD700; // Gold
                    case XP    -> 0x55FF55; // Grün
                    case DROP  -> 0x55FFFF; // Aqua
                    case IV    -> 0xAA00AA; // Lila
                };

                graphics.drawString(mc.font, text, x, y, color, true);
                y += mc.font.lineHeight + 2;
            }
        });

        EvolutionBoost.LOGGER.info("[EvolutionBoost] Client dim-boost HUD initialised.");
    }
}
