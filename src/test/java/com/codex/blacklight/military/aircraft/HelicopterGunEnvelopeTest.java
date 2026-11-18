package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterGunEnvelopeTest {
    private static final HelicopterGunEnvelope.Profile ORDINARY=HelicopterGunEnvelope.Profile.ORDINARY_127;
    private static final HelicopterGunEnvelope.Profile STRAFE=HelicopterGunEnvelope.Profile.STRAFE_151;
    private static boolean allowed(HelicopterGunEnvelope.Profile profile,Vec3 target){return HelicopterGunEnvelope.permits(profile,Vec3.ZERO,target,0,0,false);}

    @Test void ordinaryUsesActorRangeAndBelowGateWhileStrafeHasItsOwnBounds(){
        assertTrue(allowed(ORDINARY,new Vec3(0,-1,60)));
        assertFalse(allowed(ORDINARY,new Vec3(0,-1,60.001)));
        assertFalse(allowed(ORDINARY,new Vec3(0,-.1,1)));
        assertTrue(allowed(ORDINARY,new Vec3(0,-.1,1.001)));
        assertFalse(allowed(ORDINARY,new Vec3(0,0,20)));
        assertFalse(allowed(ORDINARY,new Vec3(0,1,20)));
        assertFalse(allowed(STRAFE,new Vec3(0,-1,10)));
        assertTrue(allowed(STRAFE,new Vec3(0,-1,10.001)));
        assertTrue(allowed(STRAFE,new Vec3(0,1,100)));
        assertFalse(allowed(STRAFE,new Vec3(0,1,100.001)));
    }

    @Test void gunGimbalAlignmentCannotReplaceTheStrafeBodyYawLimit(){
        Vec3 fortyDegrees=HelicopterArmament.direction(40,0).scale(30).add(0,-5,0);
        assertTrue(allowed(ORDINARY,fortyDegrees));
        assertFalse(allowed(STRAFE,fortyDegrees));
        assertTrue(allowed(STRAFE,HelicopterArmament.direction(24.99F,0).scale(30).add(0,-5,0)));
        assertFalse(allowed(STRAFE,HelicopterArmament.direction(25.01F,0).scale(30).add(0,-5,0)));
        assertFalse(allowed(ORDINARY,HelicopterArmament.direction(60.01F,0).scale(30).add(0,-5,0)));
        assertFalse(allowed(STRAFE,new Vec3(0,-5,-30)));
    }

    @Test void nativePitchNormalizesBeforeReplacingXZInsteadOfUsingGeometricElevation(){
        Vec3 forward=new Vec3(0,0,1),diagonal=new Vec3(0,-20,20);
        assertEquals(Math.atan(1/Math.sqrt(2)),HelicopterGunEnvelope.pitch(forward,diagonal),1e-12);
        assertTrue(allowed(ORDINARY,HelicopterArmament.direction(0,34).scale(30)));
        assertFalse(allowed(ORDINARY,HelicopterArmament.direction(0,36).scale(30)));
        assertFalse(allowed(ORDINARY,diagonal));
        assertTrue(HelicopterGunEnvelope.permits(ORDINARY,Vec3.ZERO,diagonal,0,0,true));
        assertTrue(allowed(STRAFE,diagonal));
        assertEquals(Math.toRadians(45),HelicopterGunEnvelope.pitch(forward,new Vec3(0,-1,0)),1e-12);
    }

    @Test void distanceYIsInTheActorBasisAndKeepsTheOriginalAbsoluteHeightLimit(){
        Vec3 side=new Vec3(10,-1,20);
        assertTrue(allowed(ORDINARY,side));
        assertFalse(HelicopterGunEnvelope.permits(ORDINARY,Vec3.ZERO,side,0,-20,false),"bank changes actor-local Y above zero despite world Y below zero");
        assertTrue(HelicopterGunEnvelope.permits(ORDINARY,Vec3.ZERO,new Vec3(0,-30,60),0,0,true));
        assertFalse(HelicopterGunEnvelope.permits(ORDINARY,Vec3.ZERO,new Vec3(0,-30.001,60),0,0,true));
        assertTrue(HelicopterGunEnvelope.permits(ORDINARY,new Vec3(200,100,-500),new Vec3(220,90,-500),90,0,false));
    }

    @Test void noSelectedThreadAndMalformedObservationsFailClosed(){
        assertFalse(allowed(HelicopterGunEnvelope.Profile.NONE,new Vec3(0,-5,20)));
        assertFalse(allowed(null,new Vec3(0,-5,20)));
        assertFalse(allowed(ORDINARY,null));
        assertFalse(allowed(ORDINARY,new Vec3(Double.NaN,0,1)));
        assertFalse(HelicopterGunEnvelope.permits(ORDINARY,Vec3.ZERO,new Vec3(0,-5,20),Float.NaN,0,false));
        assertFalse(allowed(ORDINARY,Vec3.ZERO));
    }
}
