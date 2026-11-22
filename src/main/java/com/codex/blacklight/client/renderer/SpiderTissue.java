package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Tissue follows its joint. Short strings stretch under load and draw back into their own cut ends. */
final class SpiderTissue {
    private static final String[] TEETH={"fang_00","fang_02","fang_04","fang_07","fang_08","fang_10"};
    private SpiderTissue(){}

    static void mouth(EncounterMob b,PoseStack stack,MultiBufferSource buffers,int light,float partial){
        if(!b.spider.live())return;
        var s=b.spiderData();var action=b.spider.actions;
        boolean feeding=b.spider.feeding(),qte=action.move()==SpiderActions.Move.SWALLOW;
        float age=feeding?b.level().getGameTime()-s.getLong("MealStart")+partial:action.age(partial);
        if(!feeding&&!qte)return;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int i=0;i<3;i++){
            String upper=TEETH[i],lower=TEETH[5-i];
            Vec3 a=tooth(b,upper,partial),z=tooth(b,lower,partial);
            float split=7+i*3;
            float rest=Mth.clamp((age-split)/7,0,1);
            double sag=.13+.22*(1-Mth.clamp(age/split,0,1));
            Vec3 middle=a.lerp(z,.46+i*.06).add(0,-sag,-.09);
            double thick=.035+.012*i;
            if(age<split){
                fiber(stack,out,light,a,middle,thick,0xffb58979);
                fiber(stack,out,light,middle,z,thick*.8,0xff926659);
            }else{
                Vec3 fromTip=middle.lerp(a.add(0,-.34,0),rest);
                Vec3 bottomTip=middle.lerp(z.add(0,-.11,0),rest);
                fiber(stack,out,light,a,fromTip,thick*.82,0xffb58979);
                fiber(stack,out,light,z,bottomTip,thick*.67,0xff926659);
            }
        }
    }

    private static Vec3 tooth(EncounterMob b,String joint,float partial){
        var p=SpiderAnatomy.PIVOTS.get(joint);
        var toward=new Vec3(1.04,4.12,-3.12).subtract(p).normalize();
        return new Vec3(b.spider.jointMatrix(new Matrix4f(),joint,partial).transformPosition(p.add(toward.scale(.72)).toVector3f()));
    }

    static void stump(EncounterMob b,int leg,PoseStack stack,MultiBufferSource buffers,int light,float partial){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        var kind=FleshStrike.byId(b.spider.strike(leg));
        float age=b.level().getGameTime()-b.spiderData().getLong("Break"+leg)+partial;
        double transientLift=Math.exp(-Math.max(age,0)/8);
        boolean cut=kind==FleshStrike.EDGE||kind==FleshStrike.CLAW;
        boolean pulled=kind==FleshStrike.LASH||kind==FleshStrike.MUSCLE;
        int strands=cut?6:pulled?4:3;double direction=b.spiderData().getFloat("Side"+leg);
        for(int j=0;j<strands;j++){
            double angle=j*2.39996+leg*.72;
            Vec3 base=new Vec3(.38*Math.cos(angle),.23*Math.sin(angle),.13*Math.sin(2*angle));
            double length=(.29+.065*j)*(pulled?1.7:1);
            Vec3 hook=base.add(direction*(.22+.14*transientLift),-.32*length,.17*Math.cos(angle));
            Vec3 end=hook.add(direction*(cut?.11:.24),-length*(.65+.55*transientLift*Math.sin(age*.53+angle)),.15*Math.sin(angle));
            fiber(stack,out,light,base,hook,cut?.077:.095,j%3==0?0xffb09a79:0xff753441);
            fiber(stack,out,light,hook,end,.043,j%3==0?0xff927755:0xff9a4b4e);
        }
    }

    private static void fiber(PoseStack pose,VertexConsumer out,int light,Vec3 a,Vec3 b,double radius,int color){
        if(a.distanceToSqr(b)>.0001)ConsumedMobVisuals.tube(pose,out,light,a,b,radius,color);
    }
}
