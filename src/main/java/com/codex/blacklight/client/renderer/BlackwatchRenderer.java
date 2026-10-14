package com.codex.blacklight.client.renderer;
import com.codex.blacklight.client.model.BlackwatchModel;
import com.codex.blacklight.entity.BlackwatchSoldier;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class BlackwatchRenderer extends MobRenderer<BlackwatchSoldier,BlackwatchModel>{
    private static final ResourceLocation TEXTURE=new ResourceLocation("blacklight","textures/entity/blackwatch_soldier.png");
    public BlackwatchRenderer(EntityRendererProvider.Context context,boolean heavy){super(context,new BlackwatchModel(BlackwatchModel.layer(heavy).bakeRoot()),.5F);}
    @Override public ResourceLocation getTextureLocation(BlackwatchSoldier entity){return TEXTURE;}
}
