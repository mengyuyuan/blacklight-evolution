package com.codex.blacklight.military.aircraft;

import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.vehicle.*;
import com.codex.blacklight.network.GunplayFeedbackPayload;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Helicopter30mmTest {
    @Test void nativeCannonProfileDoesNotOverwriteTankCoaxOrExistingWireIds(){
        var gun=VehicleWeaponDefinition.require("helicopter_30mm");
        assertEquals(15,GunplayEffects.weapon(gun.weapon()));
        assertEquals(12,GunplayEffects.weapon(VehicleWeaponDefinition.require("coaxial_machine_gun").weapon()));
        assertEquals(13,GunplayEffects.weapon(VehicleWeaponDefinition.require("helicopter_hellfire").weapon()));
        assertEquals(14,GunplayEffects.weapon(VehicleWeaponDefinition.require("helicopter_70mm").weapon()));
        assertEquals(4,gun.weapon().cooldownTicks());assertEquals(20,gun.weapon().damage());assertEquals(175,gun.weapon().range());
        assertEquals(VehicleWeaponDefinition.DamageClass.AUTOCANNON,gun.damageClass());
        assertTrue(GunplayEffects.bullet(15)&&GunplayEffects.cannon(15));
        assertEquals("autocannon",VehicleSounds.shotCue(15));
        assertEquals(4,VehicleWeaponDefinition.require("coaxial_machine_gun").weapon().damage());
    }
    @Test void endObserverReceivesThe175BlockShotAndTracerActuallyReachesIt(){
        Vec3 from=Vec3.ZERO,to=new Vec3(0,0,175),observer=new Vec3(0,0,180);
        assertTrue(HelicopterCannonTrace.visibleFrom(observer,from,to,112));
        assertFalse(HelicopterCannonTrace.visibleFrom(new Vec3(200,0,175),from,to,112));
        assertEquals(17.5,HelicopterCannonTrace.arrival(175));
        assertEquals(175,HelicopterCannonTrace.head(17.5,175));
        assertTrue(HelicopterCannonTrace.tail(17.5,175)<175);
        assertEquals(175,HelicopterCannonTrace.tail(19,175));
        assertTrue(new GunplayFeedbackPayload(1,15,0,2,0,0,0,0,0,175,0,0,-1,1,1).valid());
        assertFalse(new GunplayFeedbackPayload(1,16,0,2,0,0,0,0,0,175,0,0,-1,1,1).valid());
    }
    @Test void longerPhysicalRangeDoesNotEraseTheDistinctNativeAiEnvelopes(){
        assertTrue(HelicopterGunEnvelope.permits(HelicopterGunEnvelope.Profile.STRAFE_151,Vec3.ZERO,new Vec3(0,-10,90),0,0,false));
        assertFalse(HelicopterGunEnvelope.permits(HelicopterGunEnvelope.Profile.ORDINARY_127,Vec3.ZERO,new Vec3(0,-10,90),0,0,false));
        assertFalse(HelicopterGunEnvelope.permits(HelicopterGunEnvelope.Profile.STRAFE_151,Vec3.ZERO,new Vec3(0,-10,110),0,0,false));
    }
}
