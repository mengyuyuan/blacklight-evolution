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
        else if(quill.barrage()!=0){
            if(quill.barrage()==EvolvedQuill.CROWN_NEEDLE){
                pose.scale(1.12F,1.12F,1.06F);
                bloodSpear(out,pose,light);
                var wake=buffers.getBuffer(RenderType.entityTranslucentEmissive(ConsumedMobVisuals.WHITE));
                needleWake(wake,pose,light,quill.tickCount+partial);
            }else{
                surf(out,pose,light,quill.barrage()==EvolvedQuill.HIGH_SURF,quill.tickCount+partial);
            }
        }
        else for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            vertex(out,pose,0,0,.48F,0xffded1ba,light);vertex(out,pose,(float)Math.cos(a)*.055F,(float)Math.sin(a)*.055F,-.16F,0xff9c8b80,light);vertex(out,pose,(float)Math.cos(b)*.055F,(float)Math.sin(b)*.055F,-.16F,0xffb6a58f,light);vertex(out,pose,0,0,.48F,0xffded1ba,light);
        }pose.popPose();super.render(quill,yaw,partial,pose,buffers,light);
    }
    private static void needleWake(VertexConsumer out,PoseStack pose,int light,float age){
        float twist=age*.16F;
        for(int i=0;i<3;i++){
            float a=twist+i*2.0944F;
            float x=Mth.cos(a),y=Mth.sin(a);
            vertex(out,pose,.12F*x,.12F*y,-.44F,0xc2e4d1bb,light);
            vertex(out,pose,.40F*x+.10F*y,.40F*y-.10F*x,-1.65F,0xa6b96a65,light);
            vertex(out,pose,.53F*x+.23F*y,.53F*y-.23F*x,-4.5F,0x16473349,light);
            vertex(out,pose,.19F*x-.08F*y,.19F*y+.08F*x,-2.18F,0x78492334,light);
        }
    }
    /** Each crest spans its own lane. The open lane stays visibly open from either side. */
    private static void surf(VertexConsumer out,PoseStack pose,int light,boolean high,float age){
        float polarity=high?-1:1;
        for(int cut=0;cut<6;cut++){
            float a=cut/3F-1,b=(cut+1)/3F-1;
            float frontA=.37F-.37F*a*a,frontB=.37F-.37F*b*b;
            float crestA=polarity*(.18F+.12F*(float)Math.sin(age*.45F+cut*.84F));
            float crestB=polarity*(.18F+.12F*(float)Math.sin(age*.45F+(cut+1)*.84F));
            float xa=.67F*a,xb=.67F*b;
            vertex(out,pose,xa,crestA,frontA,0xffe6bbaa,light);
            vertex(out,pose,xb,crestB,frontB,0xffdba18f,light);
            vertex(out,pose,xb,0,frontB-.45F,0xff833b49,light);
            vertex(out,pose,xa,0,frontA-.45F,0xff5f2b3c,light);
            vertex(out,pose,xa,0,frontA-.45F,0xff4b2230,light);
            vertex(out,pose,xb,0,frontB-.45F,0xff793746,light);
            vertex(out,pose,xb*1.12F,-crestB*.65F,frontB-1-.28F*(cut%2),0xff261a27,light);
            vertex(out,pose,xa*1.12F,-crestA*.65F,frontA-1-.28F*((cut+1)%2),0xff321d2a,light);
        }
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
