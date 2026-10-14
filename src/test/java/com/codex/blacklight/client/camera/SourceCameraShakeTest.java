package com.codex.blacklight.client.camera;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceCameraShakeTest {
    private static SourceCameraShake shake() {
        var shake=new SourceCameraShake();
        shake.request(0,0,Vec3.ZERO,new Vec3(0,0,4));
        return shake;
    }
    @Test void frameRateDoesNotChangeCameraAtSameTime() {
        var low=shake();var high=shake();
        for(int i=0;i<9;i++)low.update(1./30);
        for(int i=0;i<36;i++)high.update(1./120);
        assertEquals(0,low.rotation().distanceTo(high.rotation()),1e-8);
        assertTrue(low.rotation().length()>0);
        var before=high.rotation();high.update(0);
        assertEquals(before,high.rotation());
    }
    @Test void noiseHasNoJumpAtSampleBoundary() {
        for(int seed=1;seed<=5;seed++)for(int i=1;i<30;i++) {
            double at=i/30.;
            assertEquals(SourceCameraShake.noise(at-1e-7,seed),SourceCameraShake.noise(at+1e-7,seed),1e-7);
            assertTrue(Math.abs(SourceCameraShake.noise(at,seed))<=1);
        }
    }
    @Test void resetAndFinishLeaveNoRecoil() {
        var shake=shake();shake.update(.25);shake.update(2);
        assertEquals(Vec3.ZERO,shake.rotation());
        shake.reset();shake.request(0,0,Vec3.ZERO,new Vec3(0,0,4));shake.update(.25);
        var fresh=shake();fresh.update(.25);
        assertEquals(fresh.rotation(),shake.rotation());
    }
}
