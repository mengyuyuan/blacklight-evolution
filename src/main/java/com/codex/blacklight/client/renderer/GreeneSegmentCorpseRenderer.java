package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.GreeneSegmentCorpseModel;
import com.codex.blacklight.entity.GreeneSegmentCorpse;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class GreeneSegmentCorpseRenderer extends GeoEntityRenderer<GreeneSegmentCorpse> {
    public GreeneSegmentCorpseRenderer(EntityRendererProvider.Context context){super(context,new GreeneSegmentCorpseModel());shadowRadius=0;}
    // Gecko derives body yaw only for LivingEntity; this detached Entity keeps
    // the mother's world-facing direction captured at the actual break.
    @Override protected void applyRotations(GreeneSegmentCorpse entity,PoseStack stack,float age,float yaw,float partial){
        super.applyRotations(entity,stack,age,entity.getYRot(),partial);
    }
    @Override public void render(GreeneSegmentCorpse entity,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(!entity.validPresentation())return;
        stack.pushPose();float s=com.codex.blacklight.combat.GreenePresentation.SCALE;stack.translate(0,.01*(1-s),0);stack.scale(s,s,s);
        try{super.render(entity,yaw,partial,stack,buffers,light);}finally{stack.popPose();}
    }
}
