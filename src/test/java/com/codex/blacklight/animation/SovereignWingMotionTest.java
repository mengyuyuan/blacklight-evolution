package com.codex.blacklight.animation;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SovereignWingMotionTest {
    @Test void loadedStrokeChangesForceTimingWithoutShorteningTheCycle(){
        assertEquals(.5,SovereignWingMotion.loadedPhase(.36,1),1e-6);
        assertEquals(.36,SovereignWingMotion.loadedPhase(.36,0),1e-6);
        assertEquals(SovereignWingMotion.loadedPhase(.2,1),SovereignWingMotion.loadedPhase(1.2,1),1e-6);
        double prior=-1;
        for(int i=0;i<1000;i++){double current=SovereignWingMotion.loadedPhase(i/1000.,1);assertTrue(current>=prior);prior=current;}
    }
    private static SovereignWingMotion.Pose pose(double phase,float forward,float lateral,float up,float turn,float brake) {
        return SovereignWingMotion.sample(phase,1,1,.4F,forward,lateral,up,turn,brake);
    }
    private static void close(SovereignWingMotion.Pose a,SovereignWingMotion.Pose b,float epsilon) {
        assertEquals(a.left().strokeDegrees(),b.left().strokeDegrees(),epsilon);
        assertEquals(a.right().strokeDegrees(),b.right().strokeDegrees(),epsilon);
        for(String side:new String[]{"left","right"})for(String joint:new String[]{"root","elbow","wrist","finger_1","finger_2","finger_3","finger_4"}) {
            var x=a.bone(side+"_"+joint);var y=b.bone(side+"_"+joint);
            assertEquals(x.x(),y.x(),epsilon);assertEquals(x.y(),y.y(),epsilon);assertEquals(x.z(),y.z(),epsilon);
        }
    }
    @Test void powerStrokeLowersActualOutboardPivotOnBothSides() {
        for(float pitch:new float[]{0,-45,-90})for(int side:new int[]{-1,1}) {
            var chest=new Quaternionf().rotationZ(.15F).rotateX((float)Math.toRadians(pitch));
            float[] heights=new float[2];int i=0;
            for(double phase:new double[]{0,.45}) {
                var p=pose(phase,0,0,0,0,0);
                var q=new Quaternionf(chest).mul(SovereignWingMotion.rootRotation(side>0?p.left():p.right(),SovereignWingMotion.strokeAxis(chest)));
                // Actual elbow minus root pivot from the runtime model, in model units.
                heights[i++]=q.transform(new Vector3f(side*5.3F,5.1F,1.57F)).y;
            }
            assertTrue(heights[0]-heights[1]>1,"Power stroke must lower the proximal spar: "+side+" at "+pitch+" heights="+heights[0]+","+heights[1]);
        }
    }
    @Test void neutralWingsMirrorAndTurningReversesTheAsymmetry() {
        var p=pose(.2,0,0,0,0,0);
        assertEquals(p.left().root().x(),p.right().root().x());
        assertEquals(p.left().root().z(),-p.right().root().z(),.00001F);
        assertEquals(p.left().root().y(),-p.right().root().y());
        var right=pose(.2,0,1,0,1,0);var left=pose(.2,0,-1,0,-1,0);
        assertEquals(right.left().root().z(),-left.right().root().z());
        assertNotEquals(right.left().root().z(),-right.right().root().z());
    }
    @Test void movementChangesStrokePlaneAndAscentAmplitude() {
        assertTrue(pose(.2,1,0,0,0,0).left().root().x()<0);
        assertTrue(pose(.2,-1,0,0,0,0).left().root().x()>0);
        assertTrue(pose(.2,0,0,0,0,1).left().root().x()>0);
        assertTrue(pose(.5,0,0,1,0,0).left().strokeDegrees()<pose(.5,0,0,0,0,0).left().strokeDegrees());
        assertTrue(pose(0,0,0,0,0,0).left().strokeDegrees()-pose(.5,0,0,0,0,0).left().strokeDegrees()>45);
        assertTrue(Math.abs(pose(.125,0,0,0,0,0).left().elbow().z()-pose(.5,0,0,0,0,0).left().elbow().z())>4);
        // Source finger tracks have different timing on the return stroke.
        assertTrue(Math.abs(BatWingAnimation.angle("Arm4.L",0,.25)-BatWingAnimation.angle("Arm4.L",0,.75))>5);
    }
    @Test void allJointsLoopContinuouslyIncludingDelayedTips() {
        close(pose(0,1,1,1,1,1),pose(1,1,1,1,1,1),.0001F);
        close(pose(-.000001,1,1,1,1,1),pose(.000001,1,1,1,1,1),.001F);
        for(double t=0;t<1;t+=1./120)close(pose(t,1,1,1,1,1),pose(t+1./120,1,1,1,1,1),4);
    }
    @Test void directionChangesAndDuplicateRenderPassesDoNotResetBeat() {
        var c=new SovereignWingMotion.Controller();
        for(double t=0;t<2;t+=1./60)c.update(t,1,1,.4F,1,0,0,0,0);
        var before=c.update(2,1,1,.4F,1,0,0,0,0);
        close(before,c.update(2,1,1,1,-1,1,1,1,1),0);
        close(before,c.update(2+1./120,1,1,1,-1,1,1,1,1),4);
    }
    @Test void groundedWingsFoldWithoutFlapping() {
        close(SovereignWingMotion.sample(0,1,0,0,0,0,0,0,0),
                SovereignWingMotion.sample(.5,1,0,1,1,1,1,1,1),0);
        assertEquals(-70,SovereignWingMotion.sample(0,1,0,0,0,0,0,0,0).left().root().y());
    }
    @Test void fullThrustKeepsAContinuousLoopAndDeepensThePowerStroke(){
        var normal=SovereignWingMotion.sample(.5,1,1,1,1,0,0,0,0,0);
        var drive=SovereignWingMotion.sample(.5,1,1,1,1,0,0,0,0,1);
        assertTrue(drive.left().strokeDegrees()<normal.left().strokeDegrees()-5);
        close(SovereignWingMotion.sample(0,1,1,1,1,1,1,1,1,1),SovereignWingMotion.sample(1,1,1,1,1,1,1,1,1,1),.0001F);
        for(double t=0;t<1;t+=1./120)
            close(SovereignWingMotion.sample(t,1,1,1,1,1,1,1,1,1),SovereignWingMotion.sample(t+1./120,1,1,1,1,1,1,1,1,1),6);
    }

    @Test void rewoundEntityClockResumesWingbeatWithoutResettingPose() {
        var c=new SovereignWingMotion.Controller();
        c.update(120,1,1,0,0,0,0,0,0);
        var before=c.update(120.05,1,1,0,0,0,0,0,0);
        close(before,c.update(0,1,1,0,0,0,0,0,0),.0001F);
        var resumed=c.update(.05,1,1,0,0,0,0,0,0);
        assertNotEquals(before.left().strokeDegrees(),resumed.left().strokeDegrees(),.01F);
        close(resumed,c.update(.05,1,1,0,0,0,0,0,0),0);
    }
}
