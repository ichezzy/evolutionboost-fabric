package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.hud.BoostHudSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Client-Seite:
 *  - empfängt Dim-Boost-Multiplikatoren vom Server
 *  - zeigt links oben ein HUD nur dann an, wenn in der aktuellen Dimension
 *    ein Dim-Boost > 1.0 aktiv ist.
 */
public final class EvolutionBoostClient implements ClientModInitializer {

    /** Pro Typ: Dimensionaler Multiplikator (aktuelle Dimension). */
    private static final EnumMap<BoostType, Double> DIM_MULTS =
            new EnumMap<>(BoostType.class);

    @Override
    public void onInitializeClient() {
        for (BoostType t : BoostType.values()) {
            DIM_MULTS.put(t, 1.0);
        }

        // Netzwerk-Receiver: nur Dim-Multiplikatoren
        ClientPlayNetworking.registerGlobalReceiver(
                BoostHudSync.DIM_HUD_PACKET,
                (client, handler, buf, responseSender) -> {
                    double[] dims = new double[BoostType.values().length];
                    for (int i = 0; i < BoostType.values().length; i++) {
                        dims[i] = buf.readDouble();
                    }
                    client.execute(() -> {
                        for (int i = 0; i < BoostType.values().length; i++) {
                            DIM_MULTS.put(BoostType.values()[i], dims[i]);
                        }
                    });
                }
        );

        // HUD links oben rendern
        HudRenderCallback.EVENT.register((GuiGraphics graphics, float tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            int x = 4;
            int y = 4;

            for (BoostType type : BoostType.values()) {
                double dim = DIM_MULTS.getOrDefault(type, 1.0);
                if (dim <= 1.0001) continue; // nur zeigen, wenn wirklich >1

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
