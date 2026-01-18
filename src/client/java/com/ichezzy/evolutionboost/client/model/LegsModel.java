package com.ichezzy.evolutionboost.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Model für Schuhe - rendert nur die Bein-Teile.
 * Basiert auf dem Artifacts Mod LegsModel.
 */
public class LegsModel extends HumanoidModel<LivingEntity> {

    public LegsModel(ModelPart part) {
        super(part, RenderType::entityCutoutNoCull);
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(leftLeg, rightLeg);
    }

    /**
     * Erstellt ein einfaches Legs-Mesh.
     */
    public static MeshDefinition createLegs(float delta, CubeListBuilder leftLeg, CubeListBuilder rightLeg) {
        CubeDeformation deformation = new CubeDeformation(delta);
        MeshDefinition mesh = createMesh(CubeDeformation.NONE, 0);

        mesh.getRoot().addOrReplaceChild(
                "left_leg",
                leftLeg.texOffs(0, 0)
                        .addBox(-2, 0, -2, 4, 12, 4, deformation),
                PartPose.offset(1.9F, 12, 0)
        );
        mesh.getRoot().addOrReplaceChild(
                "right_leg",
                rightLeg.texOffs(16, 0)
                        .addBox(-2, 0, -2, 4, 12, 4, deformation),
                PartPose.offset(-1.9F, 12, 0)
        );

        return mesh;
    }

    /**
     * Erstellt ein Boots-Mesh mit Schuhspitzen.
     * @param delta Größe der Schuhe (0.5F für normal, 1.25F für über Armor)
     */
    public static MeshDefinition createBoots(float delta) {
        return createBoots(delta, CubeListBuilder.create(), CubeListBuilder.create());
    }

    public static MeshDefinition createBoots(float delta, CubeListBuilder leftLeg, CubeListBuilder rightLeg) {
        CubeDeformation deformation = new CubeDeformation(delta, delta / 4, delta / 4);

        // Schuhspitzen
        leftLeg.texOffs(0, 16);
        leftLeg.addBox(-2, 12 - 3 + delta * 3 / 4, -3F - delta * 5 / 4, 4, 3, 1, deformation);
        rightLeg.texOffs(16, 16);
        rightLeg.addBox(-2, 12 - 3 + delta * 3 / 4, -3F - delta * 5 / 4, 4, 3, 1, deformation);

        return createLegs(delta, leftLeg, rightLeg);
    }
}
