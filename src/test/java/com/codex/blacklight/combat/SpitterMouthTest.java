package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpitterMouthTest {
    @Test void restingLocatorIsTheCenterOfTheVisibleNozzleFace(){
        var s=SpitterMouth.sample(MutantMove.SPITTER_SHOT,0,Vec3.ZERO,180);
        assertEquals(0,s.lip().x,1e-6);assertEquals(25/16.0+.01,s.lip().y,1e-6);assertEquals(-13/16.0,s.lip().z,1e-6);
        assertEquals(5/16.0,s.lip().distanceTo(s.throat()),1e-6);
    }
    @Test void animatedMouthIsNotTheStaticEyeOrRestingNozzle(){
        var rest=SpitterMouth.sample(MutantMove.SPITTER_SHOT,0,Vec3.ZERO,0);
        var release=SpitterMouth.sample(MutantMove.SPITTER_SHOT,14,Vec3.ZERO,0);
        assertTrue(release.lip().distanceTo(rest.lip())>.15);
        assertEquals(.9*5/16,release.lip().distanceTo(release.throat()),1e-6,"nozzle follows head contraction");
    }
    @Test void yawAndWorldTranslationPreserveTheMouth(){
        var origin=new Vec3(3000000.123,85.25,-8000000.456);
        for(float age:new float[]{0,10,14,24,34})for(float yaw:new float[]{0,45,90,180,271}){
            var a=SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,age,Vec3.ZERO,0);
            var b=SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,age,origin,yaw);
            double angle=Math.toRadians(-yaw),c=Math.cos(angle),s=Math.sin(angle);var p=a.lip();
            var rotated=new Vec3(p.x*c+p.z*s,p.y,p.z*c-p.x*s);
            assertEquals(0,b.lip().subtract(origin).distanceTo(rotated),2e-6);
        }
    }
    @Test void allVolleyReleasesRepeatTheSameContraction(){
        var a=SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,14,Vec3.ZERO,0);
        for(int age:new int[]{24,34})assertEquals(0,a.lip().distanceTo(SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,age,Vec3.ZERO,0).lip()),1e-6);
    }
    @Test void samplesAreContinuousAndIndependentOfCallOrder(){
        var expected=SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,24.25F,Vec3.ZERO,73);
        SpitterMouth.sample(MutantMove.SPITTER_RETREAT,26,Vec3.ZERO,-90);
        assertEquals(expected,SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,24.25F,Vec3.ZERO,73));
        for(float age=0;age<46;age+=.1F){
            var a=SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,age,Vec3.ZERO,0);
            var b=SpitterMouth.sample(MutantMove.SPITTER_VOLLEY,age+.01F,Vec3.ZERO,0);
            assertTrue(a.lip().distanceTo(b.lip())<.01);
        }
    }
}
