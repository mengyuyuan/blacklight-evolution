package com.codex.blacklight.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JointImpactTest {
    @Test void sweepSpeedAndMassChangeTheImpulseWithoutMultiplyingDuplicateEdges() {
        Vec3 slow = new Vec3(.12,0,0), fast = new Vec3(.96,0,0);
        var light = JointImpact.impulse(Vec3.ZERO,slow,1,1,true,false);
        var heavy = JointImpact.impulse(Vec3.ZERO,fast,1,1,true,false);
        assertTrue(heavy.x > light.x * 1.8);
        assertEquals(heavy,JointImpact.impulse(Vec3.ZERO,fast.scale(3),3,1,true,false));
        assertTrue(JointImpact.impulse(Vec3.ZERO,fast,1,8,true,false).x < heavy.x*.6);
        assertEquals(Vec3.ZERO,JointImpact.impulse(Vec3.ZERO,Vec3.ZERO,2,1,true,false));
    }

    @Test void weakSatelliteCannotKickAJointAsHardAsTheCenterProjectile() {
        var travel=new Vec3(1,0,0);
        double satellite=JointImpact.impulse(travel.scale(.29),travel,1,1,true,true).length();
        double center=JointImpact.impulse(travel.scale(.96),travel,1,1,true,true).length();
        assertTrue(center>satellite*2);
    }

    @Test void glancingWallHitKeepsTheDirectionOfTheSlideAndDeflectsOffTheFace() {
        var wall=new ThrownBodyWall(BlockPos.ZERO,Vec3.ZERO,new Vec3(-1,0,0),1.1);
        var result=wall.rebound(new Vec3(1.1,.5,-.8));
        assertTrue(result.x<0 && result.x>-.18 && result.y>0 && result.z<-.45);
        assertTrue(wall.rebound(new Vec3(1.1,-.4,0)).y<0);
    }
}
