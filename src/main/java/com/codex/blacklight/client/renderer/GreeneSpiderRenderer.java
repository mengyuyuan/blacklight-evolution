package com.codex.blacklight.client.renderer;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import com.codex.blacklight.entity.GreeneSpider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GreeneSpiderRenderer extends GeoEntityRenderer<GreeneSpider> {
    public GreeneSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<GreeneSpider>(ResourceLocation.fromNamespaceAndPath("blacklight", "greene_spider")));
        shadowRadius = 2.2F;
    }

    @Override protected void applyRotations(GreeneSpider entity, PoseStack stack, float age, float yaw, float partial, float scale) {
        super.applyRotations(entity, stack, age, entity.getYRot(), partial, scale);
    }
}
