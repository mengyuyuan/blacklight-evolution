package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlackwatchModel;
import com.codex.blacklight.entity.MilitarySoldier;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

/** Separate faction textures; no colour multiplier or permanently attached held-item layer. */
public final class BlackwatchRenderer extends MobRenderer<MilitarySoldier,BlackwatchModel> {
    private final ResourceLocation texture;
    private static ResourceLocation texture(String path) { return new ResourceLocation("blacklight",path); }
    public BlackwatchRenderer(EntityRendererProvider.Context context,boolean heavy) { this(context,heavy,false); }
    public BlackwatchRenderer(EntityRendererProvider.Context context,boolean heavy,boolean military) {
        super(context,new BlackwatchModel(BlackwatchModel.layer(heavy,military).bakeRoot(),military),.5F);
        texture=texture("textures/entity/"+(military?"military":"blackwatch")+"_soldier_layered.png");
    }
    @Override public ResourceLocation getTextureLocation(MilitarySoldier entity) { return texture; }
}
