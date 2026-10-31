package com.codex.blacklight.firearms;
import com.codex.blacklight.network.GunplayFeedbackPayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GunplayFeedbackPayloadTest {
    @Test void renderReceiptsRejectNonfiniteAndUnboundedGeometry(){
        assertTrue(new GunplayFeedbackPayload(1,0,0,1,0,80,0,60,80,0,0,1,0,1,7).valid());
        assertFalse(new GunplayFeedbackPayload(1,13,0,1,0,80,0,60,80,0,0,1,0,1,7).valid());
        assertFalse(new GunplayFeedbackPayload(1,0,0,1,Double.NaN,80,0,60,80,0,0,1,0,1,7).valid());
        assertFalse(new GunplayFeedbackPayload(1,0,0,1,0,80,0,6000,80,0,0,1,0,1,7).valid());
    }
    @Test void catalogueIdentitiesKeepCannonAndRiflePresentationsDistinct(){
        assertEquals(0,GunplayEffects.weapon(WeaponCatalog.RIFLE));assertTrue(GunplayEffects.bullet(0));
        var he=com.codex.blacklight.military.vehicle.VehicleWeaponDefinition.require("tank_he").weapon();
        assertEquals(10,GunplayEffects.weapon(he));assertTrue(GunplayEffects.cannon(10));assertFalse(GunplayEffects.bullet(10));
    }
}
