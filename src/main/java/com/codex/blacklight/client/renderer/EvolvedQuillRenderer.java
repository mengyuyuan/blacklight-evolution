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
    private static final float[] SPEAR_Z={-2.16F,-1.75F,-.55F,-.73F,.25F};
    private static final float[] SPEAR_RADIUS={.03F,.11F,.15F,.23F,0};
    private static final int[] SPEAR_COLOR={0xff330d19,0xff822636,0xff431824,0xffaf514a,0xffe1c3a5};
    public EvolvedQuillRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(EvolvedQuill quill){return ConsumedMobVisuals.WHITE;}
    @Override public void render(EvolvedQuill quill,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,quill.yRotO,quill.getYRot())));pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial,quill.xRotO,quill.getXRot())));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        if(quill.bloodSpear())bloodSpear(out,pose,light);
        else for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            vertex(out,pose,0,0,.48F,0xffded1ba,light);vertex(out,pose,(float)Math.cos(a)*.055F,(float)Math.sin(a)*.055F,-.16F,0xff9c8b80,light);vertex(out,pose,(float)Math.cos(b)*.055F,(float)Math.sin(b)*.055F,-.16F,0xffb6a58f,light);vertex(out,pose,0,0,.48F,0xffded1ba,light);
        }pose.popPose();super.render(quill,yaw,partial,pose,buffers,light);
    }
    private static void bloodSpear(VertexConsumer out, PoseStack pose, int light){
        for(int ring=0;ring<4;ring++){
            for(int face=0;face<5;face++){
                double a=face*2*Math.PI/5,b=(face+1)*2*Math.PI/5;
                vertex(out,pose,(float)Math.cos(a)*SPEAR_RADIUS[ring],(float)Math.sin(a)*SPEAR_RADIUS[ring],SPEAR_Z[ring],SPEAR_COLOR[ring],light);
                vertex(out,pose,(float)Math.cos(b)*SPEAR_RADIUS[ring],(float)Math.sin(b)*SPEAR_RADIUS[ring],SPEAR_Z[ring],SPEAR_COLOR[ring],light);
                vertex(out,pose,(float)Math.cos(b)*SPEAR_RADIUS[ring+1],(float)Math.sin(b)*SPEAR_RADIUS[ring+1],SPEAR_Z[ring+1],SPEAR_COLOR[ring+1],light);
                vertex(out,pose,(float)Math.cos(a)*SPEAR_RADIUS[ring+1],(float)Math.sin(a)*SPEAR_RADIUS[ring+1],SPEAR_Z[ring+1],SPEAR_COLOR[ring+1],light);
            }
        }
    }
    private static void vertex(VertexConsumer out,PoseStack pose,float x,float y,float z,int color,int light){out.addVertex(pose.last(),x,y,z).setColor(color).setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),0,1,0);}
}
