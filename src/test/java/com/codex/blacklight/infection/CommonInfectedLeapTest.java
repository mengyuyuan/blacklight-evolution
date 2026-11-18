package com.codex.blacklight.infection;

import com.codex.blacklight.client.model.CommonInfectedModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommonInfectedLeapTest {
    @Test void rootDriverExitFadesLinearlyOnceBeforeEvaluationAndDoesNotMultiplyOrdinaryMomentum(){
        var exit=new CommonInfectedLeap.RootExit();exit.begin(100,new Vec3(.4,-.7,.2));
        var ordinary=new Vec3(-.1,-.08,.03);
        assertEquals(1-.05/CommonInfectedLeap.ROOT_BLEND_OUT_SECONDS,exit.weight(100),1e-12);
        var first=exit.blend(ordinary,100);
        assertEquals(ordinary.y,first.y,0,"MC gravity/contact retains vertical ownership; this is not a bone fade");
        assertEquals(ordinary.x+(.4-ordinary.x)*exit.weight(100),first.x,1e-12);
        assertTrue(first.x<.4&&first.x>ordinary.x,"two root drivers are mixed, not added as duplicate momentum");
        exit.begin(102,new Vec3(100,100,100));
        assertEquals(first,exit.blend(ordinary,100),"repeated release does not replace velocity or restart the fade");
        assertTrue(exit.weight(105)>0);assertEquals(0,exit.weight(106),0);
        assertEquals(ordinary,exit.blend(ordinary,106));assertEquals(ordinary,exit.blend(ordinary,1000));
        exit.begin(1001,new Vec3(0,0,.2));assertTrue(exit.weight(1001)>0,"a later independent leap can own a new exit");
    }
    @Test void captureAndInvalidRootInputsCannotLeaveADeferredMovementTail(){
        var exit=new CommonInfectedLeap.RootExit();var ordinary=new Vec3(.02,-.1,.04);
        exit.begin(10,new Vec3(.4,.3,0));exit.clear();
        assertEquals(ordinary,exit.blend(ordinary,10));assertEquals(0,exit.weight(11),0);
        exit.begin(20,new Vec3(Double.NaN,0,1));assertEquals(ordinary,exit.blend(ordinary,20));
        exit.begin(30,new Vec3(0,1,0));assertEquals(0,exit.weight(30),0,"pure vertical motion is not retained by the horizontal MC adapter");
        exit.begin(40,new Vec3(.2,0,.1));assertEquals(ordinary,exit.blend(ordinary,39));
        assertEquals(0,exit.weight(40),0,"an invalid earlier clock clears the old fade instead of reviving it later");
    }
    @Test void sourceProbeIsNotTheDamagingPunchWindow(){
        assertEquals(7,CommonInfectedLeap.LAUNCH_TICK);
        assertEquals(5,CommonInfectedLeap.AIM_TICKS);
        assertEquals(5,CommonInfectedLeap.PROBE_DELAY);
        assertFalse(CommonInfectedLeap.damageWindow(3.99F));assertTrue(CommonInfectedLeap.damageWindow(4));
        assertTrue(CommonInfectedLeap.damageWindow(11));assertFalse(CommonInfectedLeap.damageWindow(12));
        assertTrue(CommonInfectedLeap.approaches(Vec3.ZERO,0,new AABB(-.3,0,1.8,.3,1.8,2.4)));
        assertFalse(CommonInfectedLeap.approaches(Vec3.ZERO,0,new AABB(2,0,1.8,2.6,1.8,2.4)));
        assertFalse(CommonInfectedLeap.touches(Vec3.ZERO,Vec3.ZERO,0,12,3,new AABB(-2,-2,-2,2,2,2)));
    }
    @Test void damageSweepMatchesTheActuallyRenderedRightWrist(){
        var root=CommonInfectedModel.layer().bakeRoot();var model=new CommonInfectedModel(root);
        for(float yaw:new float[]{0,65,180,-125})for(int punch=0;punch<=11;punch++){
            float age=12+punch;model.applyPose(CommonInfectedMotion.leap(age,punch,-1),0,0);
            var stack=new PoseStack();root.getChild("right_arm").translateAndRotate(stack);
            root.getChild("right_arm").getChild("forearm").translateAndRotate(stack);
            var nativePoint=stack.last().pose().transformPosition(new Vector3f(-1/16F,6/16F,0));
            var actual=new Vec3(nativePoint.x,1.501-nativePoint.y,-nativePoint.z).yRot((float)Math.toRadians(-yaw));
            var expected=CommonInfectedLeap.wrist(Vec3.ZERO,yaw,age,punch);
            assertEquals(0,actual.distanceTo(expected),1e-6,"yaw="+yaw+" punch="+punch);
            var skin=new AABB(actual,actual).inflate(.015);
            assertEquals(CommonInfectedLeap.damageWindow(punch),CommonInfectedLeap.touches(Vec3.ZERO,Vec3.ZERO,yaw,age,punch,skin));
        }
    }
    @Test void nativeAirTrackingTurnsInThreeDimensionsWithoutAddingSpeed(){
        var velocity=new Vec3(.4,.25,.7);var target=new Vec3(8,2,5);
        var result=CommonInfectedLeap.track(velocity,target,Vec3.ZERO,true);
        double turn=Math.toDegrees(Math.acos(velocity.normalize().dot(result.velocity().normalize())));
        assertTrue(result.active());assertEquals(2.5,turn,1e-9);
        assertEquals(velocity.length(),result.velocity().length(),1e-12);
        assertTrue(result.velocity().normalize().dot(target.normalize())>velocity.normalize().dot(target.normalize()));
    }
    @Test void nativeTrackingLeadsMotionAndStaysOffAfterPassingTheTarget(){
        var velocity=new Vec3(0,0,1);var target=new Vec3(.1,0,10);
        var stationary=CommonInfectedLeap.track(velocity,target,Vec3.ZERO,true);
        var moving=CommonInfectedLeap.track(velocity,target,new Vec3(.25,0,0),true);
        assertTrue(moving.velocity().x>stationary.velocity().x,"moving prey is led, not simply followed at its old point");
        var passed=CommonInfectedLeap.track(velocity,new Vec3(0,0,-10),Vec3.ZERO,true);
        assertFalse(passed.active());assertEquals(velocity,passed.velocity());
        var later=CommonInfectedLeap.track(passed.velocity(),target,Vec3.ZERO,passed.active());
        assertFalse(later.active());assertEquals(velocity,later.velocity(),"no U-turn/reacquisition in the same leap");
        assertFalse(CommonInfectedLeap.track(new Vec3(0,-5.1,.5),target,Vec3.ZERO,true).active());
    }
    @Test void sidestepCannotInstantlyRedirectFlightAndInvalidMotionCannotPropagate(){
        var velocity=new Vec3(0,0,.5);
        var result=CommonInfectedLeap.track(velocity,new Vec3(3,0,3),Vec3.ZERO,true);
        assertTrue(result.active());assertTrue(result.velocity().x>0);
        assertTrue(result.velocity().x<.03,"a large sidestep is approached by a small bounded turn");
        var bad=CommonInfectedLeap.track(new Vec3(Double.NaN,0,0),new Vec3(3,0,3),Vec3.ZERO,true);
        assertFalse(bad.active());assertEquals(Vec3.ZERO,bad.velocity());
    }
    @Test void turningWristUsesTheSameShortestYawPathAsRendering(){
        float previous=179,current=-179;
        var halfway=CommonInfectedLeap.wrist(Vec3.ZERO,180,17.5F,5.5F);
        var opposite=CommonInfectedLeap.wrist(Vec3.ZERO,0,17.5F,5.5F);
        assertTrue(CommonInfectedLeap.touches(Vec3.ZERO,Vec3.ZERO,previous,current,18,6,new AABB(halfway,halfway).inflate(.01)));
        assertFalse(CommonInfectedLeap.touches(Vec3.ZERO,Vec3.ZERO,previous,current,18,6,new AABB(opposite,opposite).inflate(.01)),"179 to -179 never sweeps around the front of the body");
    }
    @Test void rangedEntryIsBoundedAndDoesNotReplaceCloseMelee(){
        assertFalse(CommonInfectedLeap.range(2,0));assertTrue(CommonInfectedLeap.range(4,0));
        assertFalse(CommonInfectedLeap.range(6,0));assertFalse(CommonInfectedLeap.range(4,2));
        assertFalse(CommonInfectedLeap.range(Double.NaN,0));
    }
    @Test void staticLaunchUsesElevationAndTheSourceBallisticEquation(){
        for(double height:new double[]{-.8,0,.8}){
            var displacement=new Vec3(0,height,4);var velocity=CommonInfectedLeap.launch(displacement).scale(20);
            double elevation=Math.atan2(height,4),base=Math.toRadians(57);
            double expectedAngle=elevation>0?base+(Math.PI/2-base)*elevation/(Math.PI/2):base+base*elevation/(Math.PI/2);
            assertEquals(expectedAngle,Math.atan2(velocity.y,velocity.horizontalDistance()),1e-12);
            double flightSeconds=4/velocity.z;
            assertEquals(height,velocity.y*flightSeconds-15*flightSeconds*flightSeconds,1e-10,
                    "the decoded continuous ballistic launch reaches the static target before discrete tick adaptation");
            assertTrue(velocity.length()<=17);
        }
    }
    @Test void fiveIsOnlyTheCloseTargetSpeedAndSeventeenIsTheCap(){
        assertEquals(5,CommonInfectedLeap.launch(new Vec3(0,0,.5)).length()*20,1e-12);
        assertTrue(CommonInfectedLeap.launch(new Vec3(0,-.99,1)).length()*20<5,
                "the normal ballistic branch does not incorrectly clamp every result to minInitialVelocity");
        assertEquals(17,CommonInfectedLeap.launch(new Vec3(0,0,100)).length()*20,1e-12);
        assertEquals(Vec3.ZERO,CommonInfectedLeap.launch(Vec3.ZERO));
        assertEquals(Vec3.ZERO,CommonInfectedLeap.launch(new Vec3(Double.NaN,0,4)));
    }
    @Test void movingLaunchInterceptsHorizontalMotionAndRelativeGravity(){
        var displacement=new Vec3(0,0,4);var targetVelocity=new Vec3(.1,0,0);
        for(double targetAcceleration:new double[]{0,-.04}){
            var velocity=CommonInfectedLeap.launch(displacement,targetVelocity,targetAcceleration);
            double ticks=4/velocity.z;
            assertEquals(targetVelocity.x*ticks,velocity.x*ticks,1e-10);
            assertEquals(.5*targetAcceleration*ticks*ticks,velocity.y*ticks-.5*.075*ticks*ticks,1e-10);
            assertTrue(velocity.length()<=17/20D&&velocity.y>=0);
            assertTrue(velocity.x>0,"lead is part of initial launch, before airborne tracking");
        }
        assertEquals(CommonInfectedLeap.launch(displacement),CommonInfectedLeap.launch(displacement,new Vec3(0,0,2),0),
                "unreachable moving-target solver exhausts its bounded search and uses the native static fallback");
    }
    @Test void sourceGravityRunsAfterTrackingAndBeforeOneUnattenuatedMove(){
        var initial=new Vec3(.2,.4,.35);var target=new Vec3(2,0,4);
        var tracked=CommonInfectedLeap.track(initial,target,Vec3.ZERO,true);
        var advanced=CommonInfectedLeap.advance(initial,target,Vec3.ZERO,true);
        assertEquals(tracked.velocity().x,advanced.velocity().x,1e-12);
        assertEquals(tracked.velocity().z,advanced.velocity().z,1e-12);
        assertEquals(tracked.velocity().y-.075,advanced.velocity().y,1e-12);
        assertEquals(tracked.active(),advanced.active());
        var velocity=CommonInfectedLeap.launch(0,4);var path=CommonInfectedLeap.clearance(velocity);
        assertTrue(path.size()>2&&path.size()<=32);
        assertEquals(0,path.get(path.size()-1).y,0,"preview stops at the landing surface, not inside the floor");
        for(int n=1;n<path.size();n++){
            assertEquals(velocity.z*n,path.get(n-1).z,1e-10,"no accumulated vanilla air drag");
            assertEquals(velocity.y*n-.075*n*(n+1)/2,path.get(n-1).y,1e-10,"gravity precedes each displacement");
        }
        assertTrue(path.stream().allMatch(v->Double.isFinite(v.lengthSqr())&&v.y>=0));
        var raised=CommonInfectedLeap.clearance(CommonInfectedLeap.launch(new Vec3(0,.5,4)),.5);
        assertEquals(.5,raised.get(raised.size()-1).y,0,"raised terrain has its own landing elevation");
    }
}
