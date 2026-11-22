package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Fresh cuts stay on the struck facet while its joint recoils. */
final class SpiderLegInjury {
    static void draw(EncounterMob boss,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var data=boss.spiderData();
        for(int leg=0;leg<8;leg++){
            if(boss.spider.broken(leg) || !data.contains("Along"+leg))continue;
            float t=boss.level().getGameTime()-data.getLong("Flinch"+leg)+partial;
            if(t<0 || t>=46)continue;
            String joint=SpiderLegSkin.joint(leg,data.getBoolean("Upper"+leg));
            var skin=SpiderLegSkin.of(joint);
            double along=data.getFloat("Along"+leg),around=data.getFloat("Around"+leg);
            int kind=data.getInt("Impact"+leg);
            double span=kind==FleshStrike.QUILL.id?.17:kind==FleshStrike.HAMMER.id?.32:.58;
            double width=(.16+.14*(1-t/46));
            pose.pushPose();
            pose.mulPoseMatrix(boss.spider.jointMatrix(new Matrix4f(),joint,partial));
            var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
            int cuts=kind==FleshStrike.CLAW.id?3:1;
            for(int i=0;i<cuts;i++){
                double side=around+(i-(cuts-1)*.5)*.72;
                ribbon(pose,out,light,skin,along,side,span,width,.026,0xff4a1723);
                ribbon(pose,out,light,skin,along,side,span*.85,width*.53,.035,t<8?0xffe2a188:0xffa74248);
            }
            pose.popPose();
        }
    }
    private static void ribbon(PoseStack pose,VertexConsumer out,int light,SpiderLegSkin skin,double along,double side,double span,double width,double lift,int color){
        for(int i=0;i<6;i++){
            double a=i/6.,b=(i+1)/6.;
            double y=along+(2*a-1)*span,z=along+(2*b-1)*span;
            double x=side+(a-.5)*.9,w=width*Math.sin(a*Math.PI);
            double nx=side+(b-.5)*.9,nw=width*Math.sin(b*Math.PI);
            var p=skin.surface(y,x-w,lift);var q=skin.surface(y,x+w,lift);
            var r=skin.surface(z,nx+nw,lift);var s=skin.surface(z,nx-nw,lift);
            Vec3 normal=skin.surface(z,nx,lift+.1).subtract(skin.surface(z,nx,lift)).normalize();
            for(var v:new Vec3[]{p,q,r,s})ConsumedMobVisuals.vertex(pose,out,light,v,normal,color,.5F,.5F);
        }
    }
    private SpiderLegInjury(){}
}
