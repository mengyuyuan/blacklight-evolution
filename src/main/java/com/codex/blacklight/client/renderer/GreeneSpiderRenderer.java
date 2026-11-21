package com.codex.blacklight.client.renderer;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import com.codex.blacklight.entity.GreeneSpider;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GreeneSpiderRenderer extends GeoEntityRenderer<GreeneSpider> {
    private static final ResourceLocation BROOD = new ResourceLocation("blacklight","textures/entity/greene_spider_brood.png");
    @Override public ResourceLocation getTextureLocation(GreeneSpider entity) { return BROOD; }

    public GreeneSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<GreeneSpider>(new ResourceLocation("blacklight", "greene_spider")));
        shadowRadius = 2.2F;
    }

    @Override protected void applyRotations(GreeneSpider entity, PoseStack stack, float age, float yaw, float partial) {
        super.applyRotations(entity, stack, age, entity.getYRot(), partial);
    }
}
