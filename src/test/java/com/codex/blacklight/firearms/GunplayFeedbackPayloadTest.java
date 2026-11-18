package com.codex.blacklight.firearms;
import com.codex.blacklight.network.GunplayFeedbackPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GunplayFeedbackPayloadTest {
    @Test void renderReceiptsRejectNonfiniteAndUnboundedGeometry(){
        assertTrue(new GunplayFeedbackPayload(1,0,0,1,0,80,0,60,80,0,0,1,0,1,7).valid());
        assertTrue(new GunplayFeedbackPayload(1,13,0,1,0,80,0,60,80,0,0,1,0,1,7).valid());
        assertFalse(new GunplayFeedbackPayload(1,15,0,1,0,80,0,60,80,0,0,1,0,1,7).valid());
        assertFalse(new GunplayFeedbackPayload(1,0,0,1,Double.NaN,80,0,60,80,0,0,1,0,1,7).valid());
        assertFalse(new GunplayFeedbackPayload(1,0,0,1,0,80,0,6000,80,0,0,1,0,1,7).valid());
    }
    @Test void catalogueIdentitiesKeepCannonAndRiflePresentationsDistinct(){
        assertEquals(0,GunplayEffects.weapon(WeaponCatalog.RIFLE));assertTrue(GunplayEffects.bullet(0));
        var he=com.codex.blacklight.military.vehicle.VehicleWeaponDefinition.require("tank_he").weapon();
        var hellfire=com.codex.blacklight.military.vehicle.VehicleWeaponDefinition.require("helicopter_hellfire").weapon();
        assertEquals(13,GunplayEffects.weapon(hellfire));assertEquals(WeaponDefinition.Kind.MISSILE,hellfire.kind());assertFalse(GunplayEffects.bullet(13));assertFalse(GunplayEffects.cannon(13));
        var rapid=com.codex.blacklight.military.vehicle.VehicleWeaponDefinition.require("helicopter_70mm").weapon();assertEquals(14,GunplayEffects.weapon(rapid));assertEquals(WeaponDefinition.Kind.ROCKET,rapid.kind());assertFalse(GunplayEffects.bullet(14));assertFalse(GunplayEffects.cannon(14));
        assertTrue(new GunplayFeedbackPayload(1,14,0,1,0,80,0,60,80,0,0,1,0,1,7).valid());
        assertEquals(10,GunplayEffects.weapon(he));assertTrue(GunplayEffects.cannon(10));assertFalse(GunplayEffects.bullet(10));
    }
}
