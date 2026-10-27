package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightCommonInfected;
import com.codex.blacklight.client.model.CommonInfectedModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Independent civilian appearance; old common_infected saves use it without replacing their entities. */
public final class CommonInfectedRenderer extends HumanoidMobRenderer<BlacklightCommonInfected,CommonInfectedModel> {
    private static final ResourceLocation[] TEXTURES=java.util.stream.IntStream.range(0,4)
            .mapToObj(i->ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/infected_civilian_"+i+".png")).toArray(ResourceLocation[]::new);
    public CommonInfectedRenderer(EntityRendererProvider.Context context) {
        super(context,new CommonInfectedModel(CommonInfectedModel.layer().bakeRoot()),.45F);
    }
    @Override public ResourceLocation getTextureLocation(BlacklightCommonInfected entity) {
        return TEXTURES[entity.appearance()];
    }
}
