package com.codex.blacklight.firearms;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirearmBallisticsTest {
    @Test void grenadeLeadsMovingTargetUsingNativeDiscretePhysics(){
        var start=new Vec3(0,2,0);var target=new Vec3(0,1,20);var motion=new Vec3(.08,0,0);
        var s=FirearmBallistics.solve(start,target,motion,1.15,true);assertNotNull(s);
        assertEquals(1.15,s.velocity().length(),1e-7);
        var pos=start;var velocity=s.velocity();int full=(int)s.ticks();
        for(int i=0;i<full;i++){pos=pos.add(velocity);velocity=velocity.scale(.99).add(0,-.03,0);}
        pos=pos.add(velocity.scale(s.ticks()-full));
        assertTrue(pos.distanceTo(target.add(motion.scale(s.ticks())))<.02);
    }
    @Test void rocketAndGrenadeUseDifferentLaunchArcs(){
        var target=new Vec3(0,0,25);var rocket=FirearmBallistics.solve(Vec3.ZERO,target,Vec3.ZERO,2.2,false);
        var grenade=FirearmBallistics.solve(Vec3.ZERO,target,Vec3.ZERO,1.15,true);
        assertNotNull(rocket);assertNotNull(grenade);assertEquals(0,rocket.velocity().y,1e-9);assertTrue(grenade.velocity().y>.2);
    }
    @Test void unreachableAndNonfiniteTargetsDoNotInventAHit(){
        assertNull(FirearmBallistics.solve(Vec3.ZERO,new Vec3(0,100,100),Vec3.ZERO,1.15,true));
        assertNull(FirearmBallistics.solve(Vec3.ZERO,new Vec3(Double.NaN,0,0),Vec3.ZERO,2.2,false));
    }
}
