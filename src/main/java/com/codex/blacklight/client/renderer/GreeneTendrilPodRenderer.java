package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.GreeneTendrilPod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class GreeneTendrilPodRenderer extends EntityRenderer<GreeneTendrilPod> {
    public GreeneTendrilPodRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(GreeneTendrilPod pod){return ConsumedMobVisuals.WHITE;}
    @Override public void render(GreeneTendrilPod pod,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float t=pod.age(partial),fuse=GreeneTendrilPod.WINDUP;
        float fill=Mth.clamp(t/fuse,0,1),burst=Mth.clamp((t-fuse)/3,0,1),fade=Mth.clamp((fuse+6-t)/3,0,1);
        double beat=Math.pow(.5+.5*Math.sin(t*(.30+t*.013)),3);
        double swell=(.62+.38*fill+.17*beat)*(1-.80*burst);
        pose.pushPose();pose.translate(0,.37,0);
        QuillLesion.spider(pose,buffers,light,t,3,2.6*swell,.3+.6*beat);
        pose.popPose();
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        var origin=pod.getPosition(partial);
        for(int i=0;i<7;i++){
            double a=i*Math.PI*2/7 + .05*Math.sin(t*.27+i);
            var forward=new Vec3(Math.cos(a),0,Math.sin(a));
            var side=new Vec3(-forward.z,0,forward.x);
            double length=(.38+1.13*fill+burst*2.45)*fade;
            var from=forward.scale(.25).add(0,.39,0);
            for(int j=1;j<=3;j++){
                double u=j/3D;
                var next=forward.scale(.25+length*u).add(side.scale(Math.sin(Math.PI*u+i*.20)*(.17+fill*.23)*(1-burst)))
                        .add(0,.16+.56*Math.sin(u*Math.PI)*fill+.49*burst*u,0);
                var hit=pod.level().clip(new ClipContext(origin.add(from),origin.add(next),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,pod));
                if(hit.getType()==HitResult.Type.BLOCK)next=hit.getLocation().subtract(origin);
                if(from.distanceToSqr(next)>.001)ConsumedMobVisuals.tube(pose,out,light,from,next,(.155-.031*j)*fade,j==3?0xffb19379:0xff643443);
                if(hit.getType()==HitResult.Type.BLOCK)break;
                from=next;
            }
        }
    }
    @Override public boolean shouldRender(GreeneTendrilPod pod,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){
        return pod.shouldRender(x,y,z)&&f.isVisible(pod.getBoundingBox().inflate(GreeneTendrilPod.RADIUS));
    }
}
