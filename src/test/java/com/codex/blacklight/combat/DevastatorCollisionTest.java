package com.codex.blacklight.combat;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevastatorCollisionTest {
    @Test void painHasOneLivingCapsuleAndNoStaticSiblingDamage(){
        var ground=DevastatorCollision.tracks(WeaponMove.DEV_CRITICAL_PAIN);
        assertEquals(1,ground.size());assertEquals(4456,ground.getFirst().sourceTrack());
        assertTrue(ground.getFirst().extendingCapsule());
        assertEquals(30*DevastatorTiming.SOURCE_WORLD_SCALE,ground.getFirst().travelVelocity(),1e-6);
    }
    @Test void earlyAndLateGraveContactsRemainSeparateWindows(){
        var tracks=DevastatorCollision.tracks(WeaponMove.DEV_GROUNDSPIKE);
        assertEquals(2,tracks.size());assertTrue(tracks.getFirst().end()<tracks.getLast().begin());
        assertEquals(5,tracks.getFirst().radius());assertEquals(25,tracks.getLast().radius());
        assertEquals(250,tracks.getFirst().damage());assertEquals(150,tracks.getLast().damage());
    }
    @Test void capsuleReachesForwardActorAndRejectsRoundedCornerFalsePositive(){
        var start=new Vec3(0,1,0);var end=new Vec3(0,1,30);
        assertEquals(0,DevastatorCollision.capsuleDistanceSquared(start,end,new AABB(-.3,0,19.7,.3,2,20.3)),1e-8);
        assertEquals(2,DevastatorCollision.capsuleDistanceSquared(start,end,new AABB(1,2,10,2,3,11)),1e-7);
        assertTrue(DevastatorCollision.capsuleDistanceSquared(start,end,new AABB(-.3,0,39.7,.3,2,40.3))>90);
    }
    @Test void verticalCapsuleHandlesOverheadAndZeroLengthContact(){
        var start=new Vec3(0,1,0);var end=new Vec3(0,21,0);
        assertEquals(0,DevastatorCollision.capsuleDistanceSquared(start,end,new AABB(-.3,12,-.3,.3,14,.3)),1e-8);
        assertEquals(4,DevastatorCollision.capsuleDistanceSquared(start,start,new AABB(2,0,-1,3,2,1)),1e-8);
    }
}
