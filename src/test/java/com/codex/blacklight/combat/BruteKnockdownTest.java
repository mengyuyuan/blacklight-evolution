package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BruteKnockdownTest {
    @Test void getupKeepsTheHipAboveItsStandingHorizontalLocation(){
        var idle=CreaturePoseTable.BRUTE.sample("idle",0);
        var rest=BruteContact.pivot(idle,"right_leg").add(BruteContact.pivot(idle,"left_leg")).scale(.5);
        for(int frame=0;frame<=160;frame++){
            var p=BruteKnockdown.pose(frame/160F);
            var hip=BruteContact.pivot(p,"right_leg").add(BruteContact.pivot(p,"left_leg")).scale(.5);
            assertEquals(rest.x,hip.x,1e-6);assertEquals(rest.z,hip.z,1e-6);
        }
    }
    @Test void kneeHingeMovesOnlyItsLowerLegAndPreservesItsRadius(){
        var pose=CreaturePoseTable.BRUTE.sample("idle",0);var before=BruteContact.wholeBody(pose,Vec3.ZERO,180);
        int knee=java.util.Arrays.asList(CreaturePoseTable.BRUTE.bones).indexOf("right_shin");assertTrue(knee>=0);
        pose[knee][0]=(float)Math.toRadians(-85);var after=BruteContact.wholeBody(pose,Vec3.ZERO,180);
        var hinge=new Vec3(3.65/16,12.2/16+.01,.4/16);int moved=0;
        for(int i=0;i<before.size();i++){
            var a=before.get(i);var b=after.get(i);
            if(a.bone().equals("right_shin")){assertTrue(a.box().center.distanceTo(b.box().center)>.05);assertEquals(a.box().center.distanceTo(hinge),b.box().center.distanceTo(hinge),1e-6);moved++;}
            else assertTrue(a.box().center.distanceTo(b.box().center)<1e-6,"knee cannot move torso, thigh or other leg");
        }
        assertEquals(11,moved);assertEquals(120,BruteContact.body(pose,Vec3.ZERO,180).size());
    }
    @Test void wholeGetupKeepsRealMeshAboveTheFloorAndReturnsEveryIdleChannel(){
        java.util.List<BruteContact.Surface> previous=null;
        float[][] previousPose=null;
        for(int frame=0;frame<=160;frame++){
            var pose=BruteKnockdown.pose(frame/160F);var all=BruteContact.wholeBody(pose,Vec3.ZERO,180);
            assertEquals(152,all.size());assertEquals(120,BruteContact.body(pose,Vec3.ZERO,180).size());
            for(int i=0;i<all.size();i++){
                var box=all.get(i).box();assertTrue(Double.isFinite(box.center.lengthSqr()));
                assertTrue(box.bounds.minY>=.009,"floor frame="+frame+" bone="+all.get(i).bone()+" y="+box.bounds.minY);
                if(previous!=null&&box.center.distanceTo(previous.get(i).box().center)>=.2)fail("smooth support transfer frame="+frame+" bone="+all.get(i).bone()+" before="+java.util.Arrays.deepToString(previousPose)+" after="+java.util.Arrays.deepToString(pose));
            }
            previous=all;
            previousPose=pose;
        }
        var finalPose=BruteKnockdown.pose(1);var idle=CreaturePoseTable.BRUTE.sample("idle",0);
        for(int i=0;i<idle.length;i++)assertArrayEquals(idle[i],finalPose[i],1e-5F);
    }
    @Test void downStaysLowAndGetupUsesItsOwnDuration(){
        var down=new HitReaction(100,18,HitReaction.DOWN,0,ImpactProfile.DOWN);
        var rise=new HitReaction(118,BruteKnockdown.GETUP_TICKS,HitReaction.GETUP,0,ImpactProfile.RISE);
        assertTrue(BruteKnockdown.controlled(down));assertTrue(BruteKnockdown.controlled(rise));assertFalse(BruteKnockdown.controlled(HitReaction.EMPTY));
        double headDown=BruteContact.wholeBody(BruteKnockdown.pose(down,110,0),Vec3.ZERO,180).stream().filter(s->s.bone().equals("head")).mapToDouble(s->s.box().center.y).average().orElseThrow();
        double headUp=BruteContact.wholeBody(BruteKnockdown.pose(rise,150,0),Vec3.ZERO,180).stream().filter(s->s.bone().equals("head")).mapToDouble(s->s.box().center.y).average().orElseThrow();
        assertTrue(headUp-headDown>.5,"heavy body rises from ground support");
    }
    @Test void plantedPushKeepsBothFistsAndLowerBodyOnTheSupportPlane(){
        for(float t=.4F;t<=.78F;t+=.02F){
            var pose=BruteKnockdown.pose(t);var all=BruteContact.wholeBody(pose,Vec3.ZERO,180);
            for(String bone:new String[]{"right_forearm","left_forearm"}){
                double low=all.stream().filter(s->s.bone().equals(bone)).mapToDouble(s->s.box().bounds.minY).min().orElseThrow();
                assertEquals(low,BruteContact.lowest(pose,bone),1e-6);
                assertTrue(low>=.009&&low<.018,"planted fist t="+t+" bone="+bone+" y="+low);
            }
            double support=all.stream().filter(s->s.bone().endsWith("_leg")||s.bone().endsWith("_shin")).mapToDouble(s->s.box().bounds.minY).min().orElseThrow();
            assertTrue(support<.018,"lower body carries weight t="+t+" y="+support);
        }
    }
}
