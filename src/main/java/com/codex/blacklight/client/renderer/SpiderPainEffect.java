package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import static com.codex.blacklight.client.renderer.BlackTendrilMesh.P;

/** The player's Critical Pain strand profiles drive a coiled discharge from the spider's rig. */
final class SpiderPainEffect {
    private SpiderPainEffect(){}
    private static P point(Vec3 v){return new P(v.x,v.y,v.z);}
    static void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(boss.spider.actions.move()!=SpiderActions.Move.MOUTH_BEAM)return;
        float t=boss.spider.actions.age(partial),open=SpiderPainBeam.opening(t);
        if(open<=0 || t>=SpiderPainBeam.SHUT+8)return;
        var lane=SpiderPainBeam.lane(boss,partial);var origin=boss.getPosition(partial);
        var root=lane.mouth().subtract(origin);var axis=lane.axis();
        var side=axis.cross(new Vec3(0,1,0)).normalize();var up=side.cross(axis).normalize();
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        var dark=GreeneTendrilPodRenderer.surface(stack,out,light);
        var glow=GreeneTendrilPodRenderer.surface(stack,out,LightTexture.FULL_BRIGHT);
        double charge=Math.min(1,t/29),length=lane.end().distanceTo(lane.mouth());
        var rig=SpiderAnatomy.root(boss,partial);
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4;
            double spin=a+.83*Math.sin(t*.12+a);
            var from=new Vec3(rig.transformPosition(new org.joml.Vector3f((float)(1.04+1.34*Math.cos(a)),(float)(4.12+1.56*Math.sin(a)),-2.88F)));
            var ring=side.scale(Math.cos(spin)).add(up.scale(Math.sin(spin)));
            var mid=from.lerp(root,.55).add(axis.scale(.85*open)).add(ring.scale(.48*open));
            var end=mid.lerp(root.add(axis.scale(.50)),charge);
            BlackTendrilMesh.strand(dark,null,new P[]{point(from),point(mid),point(end)},new double[]{.08,.18*open,.12*open},.82,true,false);
            ConsumedMobVisuals.tube(stack,out,LightTexture.FULL_BRIGHT,mid,end,.045*open,i%3==0?0xffeda99a:0xffac384e);
        }
        if(t<SpiderPainBeam.FIRE || length<.25)return;
        double radius=lane.radius();if(radius<.02)return;
        int segments=Math.max(3,Math.min(27,(int)Math.ceil(length/2.1)));
        for(int branch=0;branch<7;branch++){
            var profile=P1TendrilTrack.select("pain",branch);
            double width=profile==null?1:Math.min(2,Math.max(.7,profile.width()));
            P[] path=new P[segments+1];double[] girth=new double[path.length];
            for(int j=0;j<=segments;j++){
                double u=(double)j/segments;
                double travel=t*.64-length*u*.56;
                double crest=Math.pow(.5+.5*Math.cos(travel),4);
                double a=branch*Math.PI/3+u*7.4-t*.18+.14*Math.sin(travel+branch);
                var radial=side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                double taper=.66+.34*Math.sin(Math.PI*u);
                double twist=branch==6?0:radius*(.56+.19*crest)*taper;
                var pt=root.add(axis.scale(length*u)).add(radial.scale(twist));
                path[j]=point(pt);
                girth[j]=radius*(branch==6?.63:(.24+.06*width))*taper*(.85+.23*crest);
                if(branch<6 && j>0){
                    var prev=path[j-1];
                    var stripe=pt.add(radial.scale(girth[j]*.90));
                    var edge=new Vec3(prev.x(),prev.y(),prev.z()).add(radial.scale(girth[j-1]*.90));
                    int colour=crest>.7?0xffffcbb3:(crest>.2?0xffef5148:0xffa71f36);
                    ConsumedMobVisuals.tube(stack,out,LightTexture.FULL_BRIGHT,edge,stripe,.055+.13*crest,colour);
                }
            }
            BlackTendrilMesh.strand(branch==6?glow:dark,null,path,girth,.9,true,branch==6);
        }
        if(lane.blocked()){
            var normal=lane.impactNormal();
            var tangent=normal.cross(Math.abs(normal.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0));
            var across=normal.cross(tangent);
            var tip=lane.end().subtract(origin).add(normal.scale(.09));
            for(int i=0;i<12;i++){
                double age=(t*.105+i*.381966)%1,angle=i*2.39996;
                var sweep=tangent.scale(Math.cos(angle)).add(across.scale(Math.sin(angle)));
                double flare=radius*(.5+1.6*age);
                var knee=tip.add(sweep.scale(flare*.65)).add(normal.scale(.25+.66*Math.sin(Math.PI*age)));
                var tail=tip.add(sweep.scale(flare)).add(normal.scale(.09+.15*age));
                BlackTendrilMesh.strand(dark,null,new P[]{point(tip),point(knee),point(tail)},new double[]{.20,.18*(1-age),.014},.69,true,false);
                ConsumedMobVisuals.tube(stack,out,LightTexture.FULL_BRIGHT,knee,tail,.075*(1-age),i%3==0?0xffffbe92:0xffd94343);
            }
        }
    }
}
