package com.codex.blacklight.combat;

import net.minecraft.world.phys.*;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HunterContactTest {
    @Test void sampledSourceTurnsRoundTripWithoutChangingTheRotation(){
        var table=CreaturePoseTable.HUNTER;
        for(var move:CreatureAttack.values())for(float t=0;t<move.ticks;t+=.25F)
            for(var bone:table.sample(move.clip,t)){
                var expected=new Quaternionf().rotationZYX(bone[2],bone[1],bone[0]);
                var angles=CreaturePoseTable.blend(bone,bone,.5F);
                var actual=new Quaternionf().rotationZYX(angles[2],angles[1],angles[0]);
                assertEquals(1,Math.abs(expected.dot(actual)),.00002,"full source orientation survives interpolation");
            }
        float[] source={1.4F,2.2F,-.7F,0,0,0};var blended=CreaturePoseTable.blend(source,source,0);
        assertEquals(1,Math.abs(new Quaternionf().rotationZYX(source[2],source[1],source[0]).dot(new Quaternionf().rotationZYX(blended[2],blended[1],blended[0]))),.00001);
    }
    @Test void sphereDoesNotHitAnInflatedBoxCorner(){
        var box=new AABB(0,0,0,1,1,1);var miss=new Vec3(1.3,1.3,.5);
        assertFalse(HunterContactPose.capsuleTouches(box,miss,miss,.32));
        assertTrue(HunterContactPose.capsuleTouches(box,new Vec3(1.3,.5,.5),new Vec3(1.3,.5,.5),.32));
        assertTrue(HunterContactPose.capsuleTouches(box,new Vec3(-2,.5,.5),new Vec3(2,.5,.5),.01));
    }
    @Test void contactFollowsTheCommittedBodyYawAndWorldPosition(){
        var origin=new Vec3(50,81,-30);
        for(var move:CreatureAttack.values())for(var window:move.windows()){
            float age=(window.from()+window.to())*10;
            var zero=HunterContactPose.hand(move,age,window.left(),Vec3.ZERO,0);
            var rotated=HunterContactPose.hand(move,age,window.left(),origin,90);
            assertTrue(rotated.tip().distanceTo(origin.add(-zero.tip().z,zero.tip().y,zero.tip().x))<.00001);
            assertTrue(rotated.touches(new AABB(rotated.tip(),rotated.tip()).inflate(.01)));
            assertFalse(rotated.touches(new AABB(origin.add(0,8,0),origin.add(0,9,0)).inflate(.3)));
        }
    }
    @Test void everySourceWristWindowIsBoundedAndLeftClawIsNotDuplicatedRightClaw(){
        assertEquals(5,CreatureAttack.BARRAGE.windows().length);
        assertTrue(CreatureAttack.BARRAGE.windows()[2].left());
        for(var move:CreatureAttack.values())for(var window:move.windows()){
            assertTrue(window.from()>0&&window.to()>window.from()&&window.to()*20<move.ticks-5);
            assertNull(HunterContactPose.contact(move,window,0,1,Vec3.ZERO,Vec3.ZERO,0,new AABB(-10,-10,-10,10,10,10)));
            assertNull(HunterContactPose.contact(move,window,move.ticks-1,move.ticks,Vec3.ZERO,Vec3.ZERO,0,new AABB(-10,-10,-10,10,10,10)));
        }
    }
    @Test void originalFrontAttackActuallySweepsAStandingTarget(){
        var victim=new AABB(-.3,0,1.2,.3,1.8,1.8);
        for(var move:new CreatureAttack[]{CreatureAttack.SWIPE,CreatureAttack.BARRAGE,CreatureAttack.SLAM}){
            int hits=0;
            for(int tick=1;tick<=move.ticks;tick++)for(var window:move.windows())
                if(HunterContactPose.contact(move,window,tick-1,tick,Vec3.ZERO,Vec3.ZERO,0,victim)!=null)hits++;
            assertTrue(hits>0,move+" must contact a standing target at 1.5 blocks");
        }
    }
    @Test void aiReachPreviewDoesNotParkOutsideTheAuthoredClaws(){
        assertTrue(HunterContactPose.canReach(CreatureAttack.SWIPE,Vec3.ZERO,0,new AABB(-.3,0,1.2,.3,1.8,1.8)));
        assertFalse(HunterContactPose.canReach(CreatureAttack.SWIPE,Vec3.ZERO,0,new AABB(-.3,0,3,.3,1.8,3.6)));
        assertFalse(HunterContactPose.canReach(CreatureAttack.SWIPE,Vec3.ZERO,0,new AABB(-.3,4,1.2,.3,5.8,1.8)));
    }
}
