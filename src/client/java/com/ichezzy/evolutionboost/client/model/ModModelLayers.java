package com.ichezzy.evolutionboost.client.model;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Model Layer Definitionen für EvolutionBoost Wearables.
 */
public final class ModModelLayers {
    private ModModelLayers() {}

    // Boots Layer - klein (ohne Armor) und groß (über Armor)
    public static final ModelLayerLocation BOOTS_SMALL = createLayerLocation("boots_small");
    public static final ModelLayerLocation BOOTS_LARGE = createLayerLocation("boots_large");

    private static ModelLayerLocation createLayerLocation(String name) {
        return new ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, name),
                "main"
        );
    }

    /**
     * Registriert alle Model Layer.
     * Wird vom Client Entrypoint aufgerufen.
     */
    public static void registerLayers(BiConsumer<ModelLayerLocation, Supplier<LayerDefinition>> registration) {
        // Boots ohne Armor (delta = 0.5F)
        registration.accept(BOOTS_SMALL, () -> LayerDefinition.create(
                LegsModel.createBoots(0.5F), 32, 32
        ));

        // Boots über Armor (delta = 1.25F, größer damit sie über der Armor sitzen)
        registration.accept(BOOTS_LARGE, () -> LayerDefinition.create(
                LegsModel.createBoots(1.25F), 32, 32
        ));

        EvolutionBoost.LOGGER.info("[Client] Model layers registered");
    }
}
