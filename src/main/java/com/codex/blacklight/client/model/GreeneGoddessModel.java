package com.codex.blacklight.client.model;

import com.codex.blacklight.entity.GreeneGoddess;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class GreeneGoddessModel extends GeoModel<GreeneGoddess> {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("blacklight", path); }
    private static final ResourceLocation GEOMETRY = id("geo/greene_goddess.geo.json");
    private static final ResourceLocation SKIN = id("textures/entity/greene_goddess.png");
    private static final ResourceLocation MOTION = id("animations/greene_goddess.animation.json");

    @Override public ResourceLocation getModelResource(GreeneGoddess entity) { return GEOMETRY; }
    @Override public ResourceLocation getTextureResource(GreeneGoddess entity) { return SKIN; }
    @Override public ResourceLocation getAnimationResource(GreeneGoddess entity) { return MOTION; }
}
