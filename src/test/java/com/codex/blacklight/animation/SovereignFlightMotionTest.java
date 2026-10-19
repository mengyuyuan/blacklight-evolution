package com.codex.blacklight.animation;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SovereignFlightMotionTest {
    @Test void sustainedFlightKeepsBodyAndLimbsAliveWithoutLargeFrameJumps() {
        for(String clip:new String[]{"hover","cruise","boost"}) {
            var first=FlightAnimationClips.sample(clip,0);
            for(String joint:new String[]{"Root","Torso","Chest","Arm_R","Leg_R"}) {
                double maximum=0;
                for(double time=0;time<FlightAnimationClips.duration(clip);time+=1./60) {
                    var next=FlightAnimationClips.sample(clip,time);
                    maximum=Math.max(maximum,2*Math.acos(Math.min(1,Math.abs(first.get(joint).rotation().dot(next.get(joint).rotation())))));
                }
                assertTrue(maximum>Math.toRadians(.1),clip+" frozen "+joint);
            }
        }
    }
    @Test void bodyResponseFollowsWingStrokeWithoutMovingTheNeckPivot() {
        var top=new BlacklightPose();var bottom=new BlacklightPose();
        SovereignFlightMotion.followWingbeat(top,SovereignWingMotion.sample(0,1,1,0,0,0,0,0,0),1);
        SovereignFlightMotion.followWingbeat(bottom,SovereignWingMotion.sample(.5,1,1,0,0,0,0,0,0),1);
        assertTrue(top.get("Root").translation().distance(bottom.get("Root").translation())>.025F);
        assertTrue(Math.abs(top.get("Leg_R").rotation().dot(bottom.get("Leg_R").rotation()))<.999F);
        assertFalse(top.hasTransform("Head"));
        for(String name:FlightAnimationClips.names())for(double t=0;t<FlightAnimationClips.duration(name);t+=.05)
            assertTrue(FlightAnimationClips.sample(name,t).get("Head").translation().length()<1.3F/16,"Do not float head above the neck");
    }
    private static void same(BlacklightPose a,BlacklightPose b,float eps) {
        for(var entry:a.getJointTransformData().entrySet()) {
            var x=entry.getValue();var y=b.get(entry.getKey());
            assertNotNull(y);assertTrue(Math.abs(x.rotation().dot(y.rotation()))>1-eps,entry.getKey());
            assertTrue(x.translation().distance(y.translation())<eps,entry.getKey());
        }
    }
    @Test void captureLoopsAreClosedAndSamplerDoesNotMutateAssets() {
        for(String name:new String[]{"hover","cruise","boost"}) {
            var start=FlightAnimationClips.sample(name,0);
            same(start,FlightAnimationClips.sample(name,FlightAnimationClips.duration(name)),1e-5F);
            same(start,FlightAnimationClips.sample(name,FlightAnimationClips.duration(name)-.00001),1e-4F);
            FlightAnimationClips.sample(name,0).get("Root").rotation().rotateX(1);
            same(start,FlightAnimationClips.sample(name,0),1e-5F);
        }
    }
    @Test void allClipsHaveFiniteFullBodyMotion() {
        assertEquals(6,FlightAnimationClips.names().size());
        for(String name:FlightAnimationClips.names())for(double t=0;t<=FlightAnimationClips.duration(name);t+=1./60) {
            var p=FlightAnimationClips.sample(name,t);assertEquals(12,p.getJointTransformData().size());
            for(var b:p.getJointTransformData().values())assertTrue(b.rotation().isFinite()&&b.translation().isFinite());
        }
    }
    @Test void takeoffAndLandingActuallyBendKneesAndRoot() {
        var start=FlightAnimationClips.sample("takeoff",0);
        var crouch=FlightAnimationClips.sample("takeoff",.45);
        assertTrue(Math.abs(start.get("Leg_R").rotation().dot(crouch.get("Leg_R").rotation()))<.98);
        assertTrue(crouch.get("Root").translation().y<start.get("Root").translation().y-.1);
        assertTrue(FlightAnimationClips.sample("landing",.2).get("Root").translation().y<-.1);
    }
    @Test void interruptedTransitionsStartExactlyAtVisiblePose() {
        var t=new SovereignFlightMotion.Transition();var initial=new BlacklightPose();
        var hover=FlightAnimationClips.sample("hover",0);var boost=FlightAnimationClips.sample("boost",0);
        t.apply("hover",0,.4F,hover,initial);
        var visible=t.apply("hover",.2,.4F,hover,initial);
        same(visible,t.apply("boost",.2,.3F,boost,initial),1e-5F);
        var midway=t.apply("boost",.3,.3F,boost,initial);
        same(midway,t.apply("hover",.3,.4F,hover,initial),1e-5F);
    }
    @Test void backwardTravelNeverPitchesIntoForwardFlight() {
        var back=SovereignFlightMotion.airborne(0,.8F,-1,0,0,0,false);
        var hover=SovereignFlightMotion.airborne(0,0,0,0,0,0,false);
        same(back,hover,1e-5F);
        var fly=SovereignFlightMotion.airborne(0,1,1,0,0,0,true);
        assertTrue(fly.get("Root").rotation().transform(new Vector3f(0,1,0)).z<-.8F);
    }
    @Test void timeCompressedCaptureHasNoLargeSingleFrameSpikes() {
        for(String name:FlightAnimationClips.names()) {
            var previous=FlightAnimationClips.sample(name,0);
            for(double t=1./30;t<FlightAnimationClips.duration(name);t+=1./30) {
                var next=FlightAnimationClips.sample(name,t);
                for(var entry:previous.getJointTransformData().entrySet()) {
                    float dot=Math.abs(entry.getValue().rotation().dot(next.get(entry.getKey()).rotation()));
                    double degrees=Math.toDegrees(2*Math.acos(Math.min(1,dot)));
                    assertTrue(degrees<25,name+" "+entry.getKey()+" spike: "+degrees);
                }
                previous=next;
            }
        }
    }

    @Test void clockRewindPreservesVisibleBlendAndThenCompletesIt() {
        var t=new SovereignFlightMotion.Transition();var initial=new BlacklightPose();
        var target=FlightAnimationClips.sample("boost",0);
        t.apply("boost",120,1,target,initial);
        var midway=t.apply("boost",120.25,1,target,initial);
        same(midway,t.apply("boost",0,1,target,initial),1e-5F);
        same(target,t.apply("boost",.75,1,target,initial),1e-5F);
    }
}
