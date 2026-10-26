package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Reuses the vanilla walking/attacking zombie rig, with no extra motion assets. */
public final class CommonInfectedRenderer extends HumanoidMobRenderer<BlacklightCommonInfected,ZombieModel<BlacklightCommonInfected>> {
    public CommonInfectedRenderer(EntityRendererProvider.Context context) {
        super(context,new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),.5F);
    }
    @Override public ResourceLocation getTextureLocation(BlacklightCommonInfected entity) {
        return ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");
    }
}
