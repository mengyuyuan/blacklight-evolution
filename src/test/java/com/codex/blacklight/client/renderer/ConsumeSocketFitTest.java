package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeSocketFitTest {
    private static final double EPS=1.0e-6;
    private static final Vec3 RIGHT=new Vec3(-.12,.82,0),LEFT=new Vec3(.12,.82,0);
    private static final Vec3 SHOULDER_R=new Vec3(-.3,1.44,-.06),SHOULDER_L=new Vec3(.3,1.44,-.06);
    private static Vec3[] actor(){return ConsumeSocketFit.actor(RIGHT,LEFT,SHOULDER_R,SHOULDER_L);}
    private static void near(Vec3 expected,Vec3 actual){assertTrue(expected.distanceTo(actual)<EPS,expected+" != "+actual);}

    @Test void jacketRowsPlaceBeltSeamAndChestBetweenPosedHipsAndShoulders(){
        var points=actor();
        Vec3 hips=RIGHT.lerp(LEFT,.5),shoulders=SHOULDER_R.lerp(SHOULDER_L,.5);
        near(hips.lerp(shoulders,9./12),points[0]);
        near(hips.lerp(shoulders,2./12),points[2]);
        near(hips.lerp(shoulders,.5),points[3]);
        near(LEFT,points[4]);near(RIGHT,points[1]);
        Vec3 low=new Vec3(1,.5,.2),r=new Vec3(1,.45,0),l=new Vec3(1.15,.62,.03);
        var receiver=ConsumeSocketFit.receiver(r,l,low);
        near(r,receiver[4]);near(l,receiver[2]);near(low,receiver[1]);
        near(r.lerp(l,.5).lerp(low,2./13),receiver[0]);
    }

    @Test void allActiveSourceBranchesKeepSocketsAndOnlyLiftAlongExistingBow(){
        var a=actor();
        var b=ConsumeSocketFit.receiver(new Vec3(-.12,.78,.40),new Vec3(.12,.78,.40),new Vec3(0,1.49,.32));
        var actorSkin=ConsumeSocketFit.actorSkin(a);var receiverSkin=ConsumeSocketFit.receiverSkin(b);
        var direction=new Vec3(0,0,1);
        int inside=0,after=0,changed=0;
        for(String key:new String[]{"normal","normal_head_stomp","normal_beatdown","normal_knee_head"}){
            for(var tr:ConsumeBridgeTracks.forClip(key).tracks()){
                Vec3 root=a[tr.source().ordinal()],tip= switch(tr.target()){
                    case PELVIS,NECK,HIP_L,SPINE_2,HIP_R->b[tr.target().ordinal()];
                    case SHOULDER_L->new Vec3(.27,1.39,.35);
                    case SHOULDER_R->new Vec3(-.27,1.39,.35);
                    case KNEE_R->new Vec3(-.12,.42,.41);
                };
                for(double traveled:new double[]{0,.04,.21,.44,2}){
                    Vec3[] raw=SourceTendrilPath.sampleMinecraft(root,tip,tr.circle()*.817623245,tr.roll(),tr.pitch(),tr.segments(),traveled);
                    Vec3[] fit=raw.clone();double radius=(1.5/16)*tr.scale();
                    ConsumeSocketFit.clearSkin(fit,root,tip,actorSkin,receiverSkin,radius);
                    near(root,fit[0]);Vec3 axis=tip.subtract(root).normalize();
                    if(traveled==2)near(tip,fit[fit.length-1]);
                    for(int j=0;j<fit.length;j++){
                        Vec3 old=raw[j];if(old.distanceTo(root)<EPS||old.distanceTo(tip)<EPS){near(old,fit[j]);continue;}
                        double oldIn=Math.max(actorSkin.exit(old,direction,0),receiverSkin.exit(old,direction,0));
                        double newIn=Math.max(actorSkin.exit(fit[j],direction,0),receiverSkin.exit(fit[j],direction,0));
                        if(oldIn>0)inside++;if(newIn>0)after++;
                        Vec3 delta=fit[j].subtract(old);
                        if(delta.length()>EPS){
                            changed++;
                            Vec3 rel=old.subtract(root),bow=rel.subtract(axis.scale(axis.dot(rel)));
                            near(delta.normalize(),bow.normalize());
                            assertTrue(delta.length()<=2*bow.length()+EPS);
                            assertTrue(Math.abs(axis.dot(delta))<EPS);
                        }
                    }
                }
            }
        }
        System.out.println("skin fixture: rawNodesInside="+inside+" fittedNodesInside="+after+" nodesMoved="+changed);
        assertTrue(changed>20);assertTrue(after<inside*.7,inside+" -> "+after);
    }

    @Test void p1MirrorHoldsDuringTurnsAndNearVerticalFallback(){
        Vec3 root=new Vec3(2.3,-.4,-1.7);
        for(double yaw:new double[]{0,.2,1.7,Math.PI}){
            for(Vec3 delta:new Vec3[]{new Vec3(.45,.1,.6),new Vec3(0,1,0),new Vec3(.00005,-1,.00002)}){
                Vec3 turned=new Vec3(delta.x*Math.cos(yaw)-delta.z*Math.sin(yaw),delta.y,
                        delta.z*Math.cos(yaw)+delta.x*Math.sin(yaw));
                Vec3 tip=root.add(turned);
                Vec3[] nativePath=SourceTendrilPath.sample(root,tip,.3,60,160,10,.83);
                Vec3[] world=SourceTendrilPath.sampleMinecraft(new Vec3(root.x,root.y,-root.z),
                        new Vec3(tip.x,tip.y,-tip.z),.3,60,160,10,.83);
                for(int i=0;i<world.length;i++)near(new Vec3(nativePath[i].x,nativePath[i].y,-nativePath[i].z),world[i]);
            }
        }
    }

    @Test void externalSpansAndStraightPathsAreUnaffected(){
        var skin=ConsumeSocketFit.actorSkin(actor());
        Vec3 root=new Vec3(0,.9,0),tip=new Vec3(0,1.5,0);
        var straight=SourceTendrilPath.sample(root,tip,0,10,150,10);
        var copied=straight.clone();ConsumeSocketFit.clearSkin(copied,root,tip,skin,null,.027);
        for(int i=0;i<straight.length;i++)near(straight[i],copied[i]);
        root=root.add(0,0,2);tip=tip.add(0,0,2);
        var outside=SourceTendrilPath.sample(root,tip,.3,60,120,10);
        copied=outside.clone();ConsumeSocketFit.clearSkin(copied,root,tip,skin,null,.027);
        for(int i=0;i<outside.length;i++)near(outside[i],copied[i]);
        assertNull(ConsumeSocketFit.Skin.between(Vec3.ZERO,Vec3.ZERO,tip,0));
    }
}
