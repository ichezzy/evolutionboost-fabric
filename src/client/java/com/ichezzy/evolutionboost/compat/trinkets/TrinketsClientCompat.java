package com.ichezzy.evolutionboost.compat.trinkets;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.client.model.LegsModel;
import com.ichezzy.evolutionboost.client.model.ModModelLayers;
import com.ichezzy.evolutionboost.client.renderer.BootRenderer;
import com.ichezzy.evolutionboost.item.ModItems;
import com.ichezzy.evolutionboost.item.RunningBootsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.client.TrinketRenderer;
import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side Trinkets Rendering für Running Shoes.
 * Registriert TrinketRenderer für alle Schuh-Varianten.
 */
public final class TrinketsClientCompat {
    private TrinketsClientCompat() {}

    private static boolean initialized = false;
    private static final Map<Item, BootRenderer> RENDERERS = new HashMap<>();

    /**
     * Initialisiert die Client-side Trinkets Renderer.
     * Muss nach EntityModelSet geladen ist aufgerufen werden.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            EntityModelSet modelSet = Minecraft.getInstance().getEntityModels();

            // Renderer für jede Schuh-Variante erstellen
            registerBootRenderer(ModItems.RUNNING_SHOES, "running_shoes", modelSet);
            registerBootRenderer(ModItems.GREAT_RUNNING_SHOES, "great_running_shoes", modelSet);
            registerBootRenderer(ModItems.ULTRA_RUNNING_SHOES, "ultra_running_shoes", modelSet);

            EvolutionBoost.LOGGER.info("[TrinketsClientCompat] Trinket renderers registered!");
        } catch (Exception e) {
            EvolutionBoost.LOGGER.error("[TrinketsClientCompat] Failed to initialize renderers: {}", e.getMessage());
        }
    }

    private static void registerBootRenderer(Item item, String textureName, EntityModelSet modelSet) {
        BootRenderer renderer = new BootRenderer(textureName, hasArmor -> {
            if (hasArmor) {
                return new LegsModel(modelSet.bakeLayer(ModModelLayers.BOOTS_LARGE));
            } else {
                return new LegsModel(modelSet.bakeLayer(ModModelLayers.BOOTS_SMALL));
            }
        });

        RENDERERS.put(item, renderer);
        TrinketRendererRegistry.registerRenderer(item, new RunningShoesTrinketRenderer(renderer));
    }

    /**
     * TrinketRenderer Implementation für Running Shoes.
     */
    private record RunningShoesTrinketRenderer(BootRenderer renderer) implements TrinketRenderer {

        @Override
        public void render(
                ItemStack stack,
                SlotReference slotReference,
                EntityModel<? extends LivingEntity> contextModel,
                PoseStack poseStack,
                MultiBufferSource buffer,
                int light,
                LivingEntity entity,
                float limbSwing,
                float limbSwingAmount,
                float partialTicks,
                float ageInTicks,
                float netHeadYaw,
                float headPitch
        ) {
            renderer.render(
                    stack,
                    entity,
                    poseStack,
                    buffer,
                    light,
                    limbSwing,
                    limbSwingAmount,
                    partialTicks,
                    ageInTicks,
                    netHeadYaw,
                    headPitch
            );
        }
    }
}
