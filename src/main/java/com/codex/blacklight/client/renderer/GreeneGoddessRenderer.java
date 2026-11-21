package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.GreeneGoddessModel;
import com.codex.blacklight.entity.GreeneGoddess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GreeneGoddessRenderer extends GeoEntityRenderer<GreeneGoddess> {
    public GreeneGoddessRenderer(EntityRendererProvider.Context context) {
        super(context, new GreeneGoddessModel());
        shadowRadius = 0.5F;
    }

    @Override protected void applyRotations(GreeneGoddess entity, PoseStack stack, float age, float yaw, float partial, float scale) {
        super.applyRotations(entity, stack, age, entity.getYRot(), partial, scale);
    }
}
