package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FatalWeaponImpactTest {
    private static final Vec3 FORWARD=new Vec3(0,0,1);

    @Test void lethalMotionsRoundTripWithoutCollidingWithOrdinaryReactions(){
        for(var style:FatalWeaponImpact.values()) assertSame(style,FatalWeaponImpact.fromMotion(style.motion));
        for(int i=0;i<=8;i++) assertNull(FatalWeaponImpact.fromMotion(i));
        assertNull(FatalWeaponImpact.forForm(PowerForm.BLADE));
        assertNull(FatalWeaponImpact.forForm(PowerForm.CLAWS));
        assertNotSame(FatalWeaponImpact.forForm(PowerForm.NONE),FatalWeaponImpact.forForm(PowerForm.MUSCLE_MASS));
    }
    @Test void eachFormHasItsOwnDirectionAndWeightResponse(){
        var jab=FatalWeaponImpact.FIST.launch(Vec3.ZERO,FORWARD,1,false,false);
        var lash=FatalWeaponImpact.WHIP.launch(Vec3.ZERO,FORWARD,1,false,false);
        var crush=FatalWeaponImpact.HAMMER.launch(Vec3.ZERO,FORWARD,1,false,false);
        var throwHit=FatalWeaponImpact.MUSCLE.launch(Vec3.ZERO,FORWARD,1,false,false);
        assertTrue(lash.z>jab.z&&jab.z>0&&throwHit.z>jab.z*3&&throwHit.y>jab.y);
        assertTrue(FatalWeaponImpact.HAMMER_BREAK.launch(Vec3.ZERO,FORWARD,1,false,true).y<crush.y);
        assertEquals(throwHit.scale(.5),FatalWeaponImpact.MUSCLE.launch(Vec3.ZERO,FORWARD,.5,false,false));
    }
    @Test void movesSelectTheContactEvenWhenAnotherFormIsEquipped(){
        assertSame(FatalWeaponImpact.FIST,FatalWeaponImpact.forStrike(PowerForm.HAMMERFISTS,WeaponMove.AIR_KICK));
        assertSame(FatalWeaponImpact.HAMMER,FatalWeaponImpact.forStrike(PowerForm.HAMMERFISTS,WeaponMove.HAMMER_JAB));
        assertSame(FatalWeaponImpact.HAMMER_BREAK,FatalWeaponImpact.forStrike(PowerForm.HAMMERFISTS,WeaponMove.HAMMER_JAB_CHARGED));
        assertSame(FatalWeaponImpact.MUSCLE,FatalWeaponImpact.forStrike(PowerForm.MUSCLE_MASS,WeaponMove.AIR_KICK));
    }
    @Test void theWhipComboCannotAuthorizeATipCut(){
        assertEquals(WeaponCutContact.Kind.BLUNT,WeaponCutContact.contactKind(WeaponMove.WHIP_LASH_CHARGED));
        assertEquals(WeaponCutContact.Kind.BLUNT,WeaponCutContact.contactKind(WeaponMove.WHIP_COMBO_C));
        assertEquals(WeaponCutContact.Kind.SLICE,WeaponCutContact.contactKind(WeaponMove.WHIP_SWEEP));
        assertEquals(WeaponCutContact.Kind.SLICE,WeaponCutContact.contactKind(WeaponMove.WHIP_AIR));
        assertEquals(WeaponCutContact.Kind.PUNCTURE,WeaponCutContact.contactKind(WeaponMove.WHIP_LONGSHOT));
    }
    @Test void localPressureStretchAndCrushLeaveDistantVerticesAlone(){
        var fist=FatalWeaponImpact.FIST.displace(Vec3.ZERO,Vec3.ZERO,FORWARD,1);
        var whip=FatalWeaponImpact.WHIP.displace(Vec3.ZERO,Vec3.ZERO,FORWARD,1);
        var hammer=FatalWeaponImpact.HAMMER.displace(Vec3.ZERO,Vec3.ZERO,FORWARD,1);
        var muscle=FatalWeaponImpact.MUSCLE.displace(Vec3.ZERO,Vec3.ZERO,FORWARD,1);
        assertTrue(whip.z<0&&fist.z>0&&muscle.z>fist.z&&hammer.z>muscle.z);
        for(var impact:FatalWeaponImpact.values())
            assertEquals(FORWARD.scale(2),impact.displace(FORWARD.scale(2),Vec3.ZERO,FORWARD,1));
    }
}
