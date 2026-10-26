package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulletdiveSteeringTest {
    @Test void manualSteeringUsesCameraAndNormalizesDiagonals(){
        var north=BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,0,1,0,null);
        var right=BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,0,0,1,null);
        var diagonal=BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,0,1,1,null);
        assertEquals(.32,north.z,1e-9);assertEquals(-.32,right.x,1e-9);
        assertEquals(north.length(),diagonal.length(),1e-9);
        var turned=BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,90,1,0,null);
        assertEquals(right.x,turned.x,1e-9);
    }
    @Test void releasingInputBrakesAndReverseInputDoesNotSnap(){
        Vec3 velocity=new Vec3(1.6,-10000,0);
        var reverse=BulletdiveSteering.horizontal(Vec3.ZERO,velocity,-90,-1,0,null);
        assertTrue(reverse.x>0&&reverse.x<velocity.x);
        for(int i=0;i<5;i++)velocity=BulletdiveSteering.horizontal(Vec3.ZERO,velocity,0,0,0,null);
        assertEquals(Vec3.ZERO,velocity);
    }
    @Test void verticalSpeedNeverChangesHorizontalControlOrItsSafetyBound(){
        for(double speed:new double[]{1,100,10000,Double.MAX_VALUE}){
            Vec3 velocity=new Vec3(0,-speed,0);
            for(int i=0;i<100;i++)velocity=BulletdiveSteering.horizontal(Vec3.ZERO,velocity,0,1,1,null);
            assertEquals(BulletdiveSteering.SPEED,velocity.length(),1e-9);assertEquals(0,velocity.y);
        }
    }
    @Test void guidanceSettlesOnTheTargetAndTracksChangesWithoutOvershooting(){
        Vec3 position=new Vec3(0,60000,0),velocity=Vec3.ZERO,target=new Vec3(24,64,-12);
        for(int i=0;i<100;i++){
            velocity=BulletdiveSteering.horizontal(position,velocity,0,0,0,target);
            position=position.add(velocity);
        }
        assertTrue(Math.hypot(position.x-target.x,position.z-target.z)<.001);
        target=target.add(8,0,6);
        for(int i=0;i<60;i++){
            velocity=BulletdiveSteering.horizontal(position,velocity,0,0,0,target);position=position.add(velocity);
        }
        assertTrue(Math.hypot(position.x-target.x,position.z-target.z)<.001);
        assertEquals(60000,position.y);
    }
    @Test void heldKeysOverrideLockAndNoTargetIsInventedOnRelease(){
        var guided=BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,0,0,0,new Vec3(100,0,0));
        var manual=BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,0,1,0,new Vec3(100,0,0));
        assertTrue(guided.x>0);assertEquals(0,manual.x,1e-9);assertTrue(manual.z>0);
        assertEquals(Vec3.ZERO,BulletdiveSteering.horizontal(Vec3.ZERO,Vec3.ZERO,0,0,0,null));
    }
    @Test void altitudeDoesNotReleaseAnExistingLockOrEnlargeHorizontalAcquisition(){
        assertTrue(BulletdiveSteering.retainAltitudeLock(new Vec3(0,9999999,0),false,new Vec3(50,64,0),128));
        assertFalse(BulletdiveSteering.retainAltitudeLock(new Vec3(0,9999999,0),false,new Vec3(129,64,0),128));
        assertFalse(BulletdiveSteering.retainAltitudeLock(new Vec3(0,9999999,0),true,new Vec3(0,64,0),128));
    }
    @Test void malformedInputsCannotCreateNonFiniteMotion(){
        var result=BulletdiveSteering.horizontal(Vec3.ZERO,new Vec3(Double.NaN,0,0),Float.NaN,1,0,new Vec3(Double.NaN,0,0));
        assertEquals(Vec3.ZERO,result);
    }
}
