package com.ichezzy.evolutionboost;

import com.ichezzy.evolutionboost.boost.BoostType;
import com.ichezzy.evolutionboost.client.model.ModModelLayers;
import com.ichezzy.evolutionboost.config.ClientConfig;
import com.ichezzy.evolutionboost.hud.DimBoostHudPayload;
import com.ichezzy.evolutionboost.hud.HudTogglePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;

/**
 * Client-Seite:
 * - empfängt Dim-Boost-Multiplikatoren vom Server (S2C Payload)
 * - zeigt links ein HUD (bei ca. 1/4 Bildschirmhöhe) nur dann an, wenn in der aktuellen Dimension
 *   ein Dim-Boost > 1.0 aktiv ist.
 * - Position ist unterhalb der Cobblemon Party-Anzeige
 * - setzt alle Werte zurück bei Disconnect
 * - empfängt HUD Toggle Commands vom Server
 * - registriert Model Layers für Running Shoes
 */
public final class EvolutionBoostClient implements ClientModInitializer {

    /** Pro Typ: Dimensionaler Multiplikator (aktuelle Dimension). */
    private static final EnumMap<BoostType, Double> DIM_MULTS = new EnumMap<>(BoostType.class);
    
    private static boolean trinketsClientInitialized = false;

    @Override
    public void onInitializeClient() {
        // Client-Config laden
        ClientConfig.get();

        // Defaultwerte setzen
        resetBoostValues();
        
        // --- Model Layers registrieren (für Running Shoes Rendering) ---
        ModModelLayers.registerLayers((location, supplier) -> 
                EntityModelLayerRegistry.registerModelLayer(location, supplier::get)
        );

        // --- Bei Disconnect: alle Boost-Werte zurücksetzen ---
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                resetBoostValues();
                EvolutionBoost.LOGGER.debug("[EvolutionBoost] Client disconnected - reset boost HUD values.");
            });
        });

        // --- Bei Join: sicherheitshalber auch zurücksetzen (bevor Server neue Werte schickt) ---
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                resetBoostValues();
                EvolutionBoost.LOGGER.debug("[EvolutionBoost] Client joined server - reset boost HUD values.");
                
                // Trinkets Client Rendering initialisieren (nach World Load)
                initTrinketsClient();
            });
        });

        // --- Netzwerk-Receiver: Dim-Multiplikatoren ---
        ClientPlayNetworking.registerGlobalReceiver(
                DimBoostHudPayload.TYPE,
                (payload, context) -> {
                    double[] dims = payload.multipliers();
                    Minecraft client = context.client();

                    client.execute(() -> {
                        int len = Math.min(BoostType.values().length, dims.length);
                        for (int i = 0; i < len; i++) {
                            DIM_MULTS.put(BoostType.values()[i], dims[i]);
                        }
                    });
                }
        );

        // --- Netzwerk-Receiver: HUD Toggle vom Server ---
        ClientPlayNetworking.registerGlobalReceiver(
                HudTogglePayload.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        switch (payload.action()) {
                            case HudTogglePayload.ACTION_ON -> {
                                ClientConfig.setHudEnabled(true);
                                if (client.player != null) {
                                    client.player.sendSystemMessage(Component.literal("✓ Boost HUD enabled")
                                            .withStyle(ChatFormatting.GREEN));
                                }
                            }
                            case HudTogglePayload.ACTION_OFF -> {
                                ClientConfig.setHudEnabled(false);
                                if (client.player != null) {
                                    client.player.sendSystemMessage(Component.literal("✗ Boost HUD disabled")
                                            .withStyle(ChatFormatting.RED));
                                }
                            }
                            case HudTogglePayload.ACTION_STATUS -> {
                                boolean enabled = ClientConfig.isHudEnabled();
                                if (client.player != null) {
                                    client.player.sendSystemMessage(Component.literal("✦ Boost HUD: ")
                                            .withStyle(ChatFormatting.GOLD)
                                            .append(Component.literal(enabled ? "ON" : "OFF")
                                                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
                                    client.player.sendSystemMessage(Component.literal("  Use /eb hud on/off to toggle")
                                            .withStyle(ChatFormatting.GRAY));
                                }
                            }
                            case HudTogglePayload.ACTION_TOGGLE -> {
                                boolean newState = !ClientConfig.isHudEnabled();
                                ClientConfig.setHudEnabled(newState);
                                if (client.player != null) {
                                    client.player.sendSystemMessage(Component.literal(newState ? "✓ Boost HUD enabled" : "✗ Boost HUD disabled")
                                            .withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.RED));
                                }
                            }
                        }
                    });
                }
        );

        // --- HUD links rendern (Position passt sich an GUI-Scale an) ---
        HudRenderCallback.EVENT.register((GuiGraphics graphics, DeltaTracker deltaTracker) -> {
            // HUD deaktiviert?
            if (!ClientConfig.isHudEnabled()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Nicht anzeigen wenn F3 Debug-Screen offen ist
            if (mc.getDebugOverlay().showDebugScreen()) return;

            // Position aus Config
            ClientConfig config = ClientConfig.get();
            int screenHeight = mc.getWindow().getGuiScaledHeight();
            int x = config.hudX;
            int y = Math.max(30, (int)(screenHeight * config.hudYPercent));

            for (BoostType type : BoostType.values()) {
                double dim = DIM_MULTS.getOrDefault(type, 1.0D);
                if (dim <= 1.0001D) continue; // nur zeigen, wenn wirklich >1

                String label = switch (type) {
                    case SHINY -> "Shiny";
                    case XP    -> "XP";
                    case EV    -> "EV";
                    case IV    -> "IV";
                };

                String text = String.format(Locale.ROOT, "[Boost] %s x%.2f", label, dim);

                int color = switch (type) {
                    case SHINY -> 0xFFD700; // Gold
                    case XP    -> 0x55FF55; // Grün
                    case EV    -> 0x55FFFF; // Aqua
                    case IV    -> 0xAA00AA; // Lila
                };

                graphics.drawString(mc.font, text, x, y, color, true);
                y += mc.font.lineHeight + 2;
            }
        });

        EvolutionBoost.LOGGER.info("[EvolutionBoost] Client dim-boost HUD initialised.");
    }

    /**
     * Setzt alle Boost-Multiplikatoren auf 1.0 zurück.
     */
    private static void resetBoostValues() {
        for (BoostType t : BoostType.values()) {
            DIM_MULTS.put(t, 1.0D);
        }
    }
    
    /**
     * Initialisiert Trinkets Client Rendering (nur wenn Trinkets geladen ist).
     */
    private static void initTrinketsClient() {
        if (trinketsClientInitialized) return;
        
        if (FabricLoader.getInstance().isModLoaded("trinkets")) {
            try {
                com.ichezzy.evolutionboost.compat.trinkets.TrinketsClientCompat.init();
                trinketsClientInitialized = true;
            } catch (NoClassDefFoundError e) {
                EvolutionBoost.LOGGER.warn("[EvolutionBoost] Trinkets client classes not found - skipping renderer registration");
            }
        }
    }
}