package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessStruggle;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Three locks of hair gather at the snare; relief and pulse stay visible in shaderpack world passes. */
final class GoddessStruggleRenderer {
    private static void cable(PoseStack stack,VertexConsumer out,int light,Vec3 origin,Vec3 a,Vec3 mid,Vec3 end,
                              Vec3 side,double radius,int color){
        Vec3[] path=new Vec3[9];double[] widths=new double[path.length];
        for(int i=0;i<path.length;i++){
            double t=i/8.;path[i]=a.lerp(mid,t).lerp(mid.lerp(end,t),t).subtract(origin);
            widths[i]=radius*(.47+.53*Math.sin(Math.PI*t));
        }
        ConsumeTendrilSegmentMesh.draw(stack,out,light,path,widths,side,color,false);
    }
    static void draw(EncounterMob b,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var q=b.goddess.attacks.struggle;
        if(!q.active())return;
        var origin=b.getPosition(partial);var right=q.right();var front=q.front();
        var out=buffers.getBuffer(RenderType.entityTranslucent(ConsumeTendrilSegmentMesh.TEXTURE));
        var step=q.step();float age=q.age(partial);
        if(!q.hair()){
            if(step!=GoddessStruggle.Step.SLIP && step!=GoddessStruggle.Step.JUMP)return;
            boolean shut=step==GoddessStruggle.Step.SLIP;
            double radius=shut?1.26-.8*Mth.clamp(age/20,0,1):1.28;
            var at=q.seat(partial).add(0,1,0);
            for(int i=0;i<8;i++){
                double a=i*Math.PI/4;
                var radial=right.scale(Math.cos(a)).add(front.scale(Math.sin(a)));
                cable(stack,out,light,origin,at.add(0,-.8,0),at.add(radial.scale(radius*1.6)),at.add(0,.95,0),
                        right,.065+(shut?.11*age/22:0),shut?0xe3321423:0xbce99b83);
            }
            return;
        }
        var crown=b.hurtRegions(partial).get(11).boxes().get(0).center;
        var at=step==GoddessStruggle.Step.WARN || step==GoddessStruggle.Step.OPEN?q.mark():q.seat(partial);
        float progress=step==GoddessStruggle.Step.WARN?Mth.clamp(age/20,0,1):step==GoddessStruggle.Step.OPEN?Mth.clamp(1-age/15,0,1):1;
        boolean telegraph=step==GoddessStruggle.Step.WARN||step==GoddessStruggle.Step.OPEN;
        if(telegraph){
            for(int i=0;i<18;i++){
                double a=i*Math.PI/9,d=Math.PI/9;
                var start=at.add(2.15*Math.cos(a),.20,2.15*Math.sin(a));
                var end=at.add(2.15*Math.cos(a+d),.20,2.15*Math.sin(a+d));
                cable(stack,out,light,origin,start,start.lerp(end,.5).add(0,.11,0),end,front,.09,0xd455304e);
            }
        }
        for(int i=0;i<3;i++){
            if(i<2 && (q.knotMask()&(1<<i))!=0)continue;
            double angle=i*2*Math.PI/3;
            var start=crown.add(Math.cos(angle)*.9,0,Math.sin(angle)*.9);
            var target=i==2?q.supportTip(partial):telegraph?at.add(Math.cos(angle)*1.85,.28,Math.sin(angle)*1.85):
                    q.knot(i,partial);
            var bend=start.lerp(target,.48).add(right.scale((i-1)*(telegraph?5:2.4))).add(0,-2,0);
            var tip=start.lerp(target,progress);
            cable(stack,out,light,origin,start,bend,tip,front,i==2?.24:.115,0xe553303d);
            if(!telegraph){
                var inner=at.add(0,i==2?.26:1.17,0);
                boolean live=q.prompt() && (q.step()==GoddessStruggle.Step.LEFT?i==0:q.step()==GoddessStruggle.Step.RIGHT?i==1:
                        i==(q.remainingKnot()==GoddessStruggle.LEFT?0:1));
                cable(stack,out,live?0xf000f0:light,origin,tip,tip.add(right.scale(i==0?-.49:.49)).add(0,.37,0),inner,
                        front,live?.22:.16,live?0xffe49094:0xe55f2938);
                if(live){
                    // The raised knot has its own silhouette against the ropes under shaderpacks.
                    for(int turn=0;turn<8;turn++){
                        double a=turn*Math.PI/4,beta=a+Math.PI/4;
                        Vec3 u=tip.add(right.scale(.21*Math.cos(a))).add(0,.21*Math.sin(a),0);
                        Vec3 v=tip.add(right.scale(.21*Math.cos(beta))).add(0,.21*Math.sin(beta),0);
                        cable(stack,out,0xf000f0,origin,u,u.lerp(v,.5).add(front.scale(.035)),v,front,.12,0xfff3b6ae);
                    }
                }
                if(step==GoddessStruggle.Step.SLIP){
                    double pulse=Mth.clamp((age-i*2)/16.,0,1);
                    var core=target.lerp(start,pulse);
                    cable(stack,out,0xf000f0,origin,core,core.lerp(start,.02).add(right.scale(.22)),core.lerp(start,.04),front,.24,0xffc86b84);
                }
            }
        }
    }
    private GoddessStruggleRenderer(){}
}
