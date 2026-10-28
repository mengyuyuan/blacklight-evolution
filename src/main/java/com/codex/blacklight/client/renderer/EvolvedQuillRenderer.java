package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.EvolvedQuill;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class EvolvedQuillRenderer extends EntityRenderer<EvolvedQuill> {
    public EvolvedQuillRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(EvolvedQuill quill){return ConsumedMobVisuals.WHITE;}
    @Override public void render(EvolvedQuill quill,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,quill.yRotO,quill.getYRot())));pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial,quill.xRotO,quill.getXRot())));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            vertex(out,pose,0,0,.48F,0xffded1ba,light);vertex(out,pose,(float)Math.cos(a)*.055F,(float)Math.sin(a)*.055F,-.16F,0xff9c8b80,light);vertex(out,pose,(float)Math.cos(b)*.055F,(float)Math.sin(b)*.055F,-.16F,0xffb6a58f,light);vertex(out,pose,0,0,.48F,0xffded1ba,light);
        }pose.popPose();super.render(quill,yaw,partial,pose,buffers,light);
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float z,int color,int light){out.addVertex(pose.last(),x,y,z).setColor(color).setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,1,0);}
}
