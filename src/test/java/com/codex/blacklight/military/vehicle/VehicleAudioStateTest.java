package com.codex.blacklight.military.vehicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VehicleAudioStateTest {
    @Test void reversingAndTurningHaveMovementButIdleDoesNot(){
        assertEquals(1,VehicleAudioState.load(-.3,0,.3));assertEquals(.55,VehicleAudioState.load(0,3,.3),1e-5);
        assertEquals(0,VehicleAudioState.load(0,0,.3));assertEquals(0,VehicleAudioState.load(Double.NaN,0,.3));
        for(String chassis:new String[]{"truck","apc","tank"}){
            var idle=VehicleAudioState.mix(true,0,chassis);var moving=VehicleAudioState.mix(true,1,chassis);var off=VehicleAudioState.mix(false,1,chassis);
            assertTrue(idle.engine()>0);assertEquals(0,idle.rolling());assertTrue(moving.engine()>idle.engine());assertTrue(moving.enginePitch()>idle.enginePitch());
            assertTrue(moving.rolling()>0);assertEquals(0,off.engine());assertEquals(0,off.rolling());
        }
    }
    @Test void stopsFadeToExactSilenceAndMovingPitchRemainsBounded(){
        float v=.65F;for(int i=0;i<20;i++)v=VehicleAudioState.approach(v,0);assertEquals(0,v);
        assertTrue(VehicleAudioState.mix(true,Float.POSITIVE_INFINITY,"tank").enginePitch()<2);
    }
    @Test void everyInstalledWeaponHasItsOwnFamily(){
        assertEquals("autocannon",VehicleSounds.shotCue(8));assertEquals("rocket",VehicleSounds.shotCue(9));
        assertEquals("cannon",VehicleSounds.shotCue(10));assertEquals("cannon",VehicleSounds.shotCue(11));
        assertEquals("coaxial",VehicleSounds.shotCue(12));assertEquals("",VehicleSounds.shotCue(0));
    }
}
