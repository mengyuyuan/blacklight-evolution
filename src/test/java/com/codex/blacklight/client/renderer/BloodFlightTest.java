package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloodFlightTest {
    @Test void everyWeaponMoveHasBoundedDeterministicFlightAndAContactTier() {
        for (var move : WeaponMove.values()) {
            for (var form : PowerForm.values()) {
                if (!move.accepts(form)) continue;
                var strike=FleshStrike.of(form,move);
                var profile=BloodFlight.of(strike,strike.style(move.form==PowerForm.BLADE||move.form==PowerForm.CLAWS?WeaponCutContact.Kind.SLICE:WeaponCutContact.Kind.BLUNT));
                var seed=RandomSource.create(53);
                var copy=RandomSource.create(53);
                for(int i=0;i<profile.drops;i++){
                    var v=profile.velocity(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),Vec3.ZERO,1.6,seed);
                    assertEquals(v,profile.velocity(new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),Vec3.ZERO,1.6,copy));
                    assertTrue(v.z>0 && v.lengthSqr()<1.3,move.id);
                }
                assertNotNull(ImpactTier.of(move), move.id);
            }
        }
        assertEquals(BloodFlight.RAKE,BloodFlight.of(FleshStrike.of(PowerForm.CLAWS,WeaponMove.CLAW_AUTO1),InfectedAttackStyle.TEAR));
        assertEquals(BloodFlight.CUT,BloodFlight.of(FleshStrike.of(PowerForm.BLADE,WeaponMove.BLADE_CUT),InfectedAttackStyle.TEAR));
        assertEquals(BloodFlight.ENTRY,BloodFlight.of(FleshStrike.QUILL,InfectedAttackStyle.PIERCE));
    }
    @Test void obliqueSplatsPointDownstreamInTheContactPlane() {
        var normal=new Vec3(0,1,0);
        assertEquals(Vec3.ZERO,BloodFlight.surfaceStretch(new Vec3(0,-.4,0),normal));
        var left=BloodFlight.surfaceStretch(new Vec3(-.45,-.12,.17),normal);
        assertTrue(left.x<-.5 && left.z>0 && left.y==0);
        assertTrue(left.length()<1.7);
    }
}
