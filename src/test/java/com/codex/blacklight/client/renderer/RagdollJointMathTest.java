package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollJointMathTest {
    @Test void interpolatedSkinRemainsAboveFloorBetweenClearRotatedEndpoints(){
        var skin=new java.util.ArrayList<Vec3>();
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1})skin.add(new Vec3(x*2,y*.1,z*.1));
        var before=new Quaternionf().rotationZ((float)Math.toRadians(60));
        var after=new Quaternionf().rotationZ((float)Math.toRadians(120));
        double y=1.79;boolean neededLift=false;
        assertEquals(0,AdaptiveRagdoll.meshFloorLift(skin,p->p,Vec3.ZERO,before,1,y,0));
        assertEquals(0,AdaptiveRagdoll.meshFloorLift(skin,p->p,Vec3.ZERO,after,1,y,0));
        for(int i=0;i<=100;i++){
            var turn=new Quaternionf(before).slerp(after,i/100F);
            double lift=AdaptiveRagdoll.meshFloorLift(skin,p->p,Vec3.ZERO,turn,1,y,0);neededLift|=lift>0;
            for(var p:skin)assertTrue(y+lift+turn.transform(p.toVector3f()).y>=-1e-6);
        }
        assertTrue(neededLift);
    }
    @Test void emptyBoundingCornersAndAbsentSupportDoNotLiftTheVisibleSkin(){
        var skin=java.util.List.of(new Vec3(-1,.1,0),new Vec3(1,-.1,0));
        var turn=new Quaternionf().rotationZ((float)Math.PI/4);
        // The enclosing box dips below zero, but this diagonal skin stays above it.
        assertEquals(0,AdaptiveRagdoll.meshFloorLift(skin,p->p,Vec3.ZERO,turn,1,.7,0));
        assertEquals(0,AdaptiveRagdoll.meshFloorLift(skin,p->p,Vec3.ZERO,turn,1,-3,Double.NEGATIVE_INFINITY));
        var centre=new Vec3(3e7,251,-3e7);
        var offset=skin.stream().map(centre::add).toList();
        double lift=AdaptiveRagdoll.meshFloorLift(offset,p->p,centre,turn,.5F,251.1,251);
        for(var p:offset)assertTrue(251.1+lift+turn.transform(p.subtract(centre).scale(.5).toVector3f()).y>=251-1e-6);
    }
    @Test void severedCentresInheritTheRotatingBodysLocalVelocity(){
        var linear=new Vec3(.2,.1,-.3);var omega=new Vec3(0,0,.4);var identity=new Quaternionf();
        var right=AdaptiveRagdoll.fragmentVelocity(linear,omega,new Vec3(.5,0,0),identity);
        var left=AdaptiveRagdoll.fragmentVelocity(linear,omega,new Vec3(-.5,0,0),identity);
        assertEquals(.3,right.y,1e-6);assertEquals(-.1,left.y,1e-6);
        assertEquals(0,right.add(left).scale(.5).distanceTo(linear),1e-6);
        assertEquals(linear,AdaptiveRagdoll.fragmentVelocity(linear,omega,Vec3.ZERO,identity));
        var rotated=AdaptiveRagdoll.fragmentVelocity(linear,omega,new Vec3(.5,0,0),new Quaternionf().rotationZ((float)Math.PI/2));
        assertEquals(0,rotated.distanceTo(linear.add(-.2,0,0)),1e-6);
    }
    @Test void smallSettlingTorquesRemainFiniteAndTurnTowardTheJoint(){
        // Regression: this nonzero cross product is below Vec3.normalize's 1e-4 dead zone.
        var turn=AdaptiveRagdoll.jointCorrection(new Vec3(1,0,0),new Vec3(0,.00001,0));
        assertTrue(turn.isFinite());
        var arm=turn.transform(new Vector3f(1,0,0));
        assertTrue(arm.y>0);assertEquals(1,arm.length(),1e-6);
        var settled=new Quaternionf();
        for(int i=0;i<2000;i++)settled.premul(turn).normalize();
        assertTrue(settled.isFinite());assertEquals(1,settled.lengthSquared(),1e-5);
    }
    @Test void zeroLeverAndZeroErrorDoNotInventRotation(){
        assertEquals(new Quaternionf(),AdaptiveRagdoll.jointCorrection(Vec3.ZERO,new Vec3(0,1,0)));
        assertEquals(new Quaternionf(),AdaptiveRagdoll.jointCorrection(new Vec3(1,0,0),Vec3.ZERO));
    }
    @Test void largeCorrectionIsBounded(){
        var turn=AdaptiveRagdoll.jointCorrection(new Vec3(.1,0,0),new Vec3(0,100,0));
        assertTrue(turn.isFinite());assertEquals(.06,2*Math.acos(turn.w),1e-5);
    }
}
