package com.ichezzy.evolutionboost.client.renderer;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.client.model.LegsModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Renderer für Running Shoes.
 * Rendert die Schuhe am Spieler wenn sie im Trinket-Slot equipt sind.
 */
public class BootRenderer {

    private final ResourceLocation texture;
    private final LegsModel model;
    private final LegsModel armorModel;

    /**
     * Erstellt einen neuen BootRenderer.
     * @param textureName Name der Textur (ohne Pfad/Extension)
     * @param modelFactory Factory die das Model erstellt (Boolean = hat Armor)
     */
    public BootRenderer(String textureName, Function<Boolean, LegsModel> modelFactory) {
        this.texture = ResourceLocation.fromNamespaceAndPath(
                EvolutionBoost.MOD_ID,
                "textures/entity/wearable/" + textureName + ".png"
        );
        this.model = modelFactory.apply(false);
        this.armorModel = modelFactory.apply(true);
    }

    /**
     * Wählt das passende Model basierend darauf ob der Spieler Boots trägt.
     */
    protected HumanoidModel<LivingEntity> getModel(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).isEmpty() ? model : armorModel;
    }

    /**
     * Rendert die Schuhe.
     */
    public void render(
            ItemStack stack,
            LivingEntity entity,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        HumanoidModel<LivingEntity> model = getModel(entity);

        // Animation setup
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        
        // Körper-Rotation folgen
        followBodyRotations(entity, model);
        
        // Rendern
        RenderType renderType = model.renderType(texture);
        VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(buffer, renderType, false, stack.hasFoil());
        model.renderToBuffer(poseStack, vertexConsumer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
    }

    /**
     * Kopiert die Körper-Rotationen vom Entity-Renderer zum Model.
     */
    @SuppressWarnings("unchecked")
    private static void followBodyRotations(LivingEntity entity, HumanoidModel<LivingEntity> model) {
        EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(entity);

        if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
            EntityModel<?> entityModel = livingRenderer.getModel();
            if (entityModel instanceof HumanoidModel<?> bipedModel) {
                ((HumanoidModel<LivingEntity>) bipedModel).copyPropertiesTo(model);
            }
        }
    }
}
