package com.codex.blacklight.client.renderer;

import com.codex.blacklight.animation.RooftopRig;
import com.codex.blacklight.entity.BackspineQuill;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import com.codex.blacklight.animation.ScapulaMount;

public final class BackspineQuillRenderer extends EntityRenderer<BackspineQuill> {
    public BackspineQuillRenderer(EntityRendererProvider.Context ctx){super(ctx);}
    @Override public ResourceLocation getTextureLocation(BackspineQuill quill){return RoofPerformance.ATLAS;}
    @Override public void render(BackspineQuill quill,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var delta=quill.getDeltaMovement().normalize();if(delta.lengthSqr()<.01)return;
        var trail=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        var origin=quill.getPosition(partial);
        var from=net.minecraft.world.phys.Vec3.ZERO;
        for(int i=0;i<(quill.heavy()?3:2);i++){
            var passed=quill.pastPosition(i);
            if(passed==null)break;
            var to=passed.subtract(origin);
            if(from.distanceToSqr(to)>16)break;
            if(from.distanceToSqr(to)>.0001){
                double width=(quill.heavy()?.052:.021)/(1+i);
                int color=quill.heavy()?(i==0?0xffad4852:0xff572936):(i==0?0xffbc8580:0xff653742);
                ConsumedMobVisuals.tube(stack,trail,light,from,to,width,color);
            }
            from=to;
        }
        stack.pushPose();
        float age=Math.max(0,quill.tickCount-1+partial);
        float turn=Math.min(1,age/3);turn=turn*turn*(3-2*turn);
        var heading=new org.joml.Vector3f((float)delta.x,(float)delta.y,(float)delta.z);
        boolean right=quill.side()==0;
        var cruising=new Quaternionf().rotationTo(new org.joml.Vector3f(0,1,-.20F).normalize(),heading);
        var orientation=quill.releaseOrientation().slerp(cruising,turn);
        stack.mulPose(new Quaternionf().rotationAxis((right?-1:1)*age*.14F,heading).mul(orientation));
        RoofPerformance.detachedQuill(quill.roofIndex(),stack,buffers,light);
        stack.popPose();
        super.render(quill,yaw,partial,stack,buffers,light);
    }
}
