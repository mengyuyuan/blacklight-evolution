package com.codex.blacklight.combat;

import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.network.InfectedImpactPayload;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FleshStrikeTest {
    @Test void jabUsesEquippedPowerAndSpecialActionsKeepTheirOwnFeedback(){
        assertEquals(FleshStrike.FIST,FleshStrike.of(PowerForm.NONE,WeaponMove.MUSCLE_JAB));
        assertEquals(FleshStrike.MUSCLE,FleshStrike.of(PowerForm.MUSCLE_MASS,WeaponMove.MUSCLE_JAB));
        assertEquals(FleshStrike.LASH,FleshStrike.of(PowerForm.WHIPFIST,WeaponMove.WHIP_LASH));
        assertEquals(FleshStrike.HAMMER,FleshStrike.of(PowerForm.HAMMERFISTS,WeaponMove.HAMMER_JAB_CHARGED));
        assertEquals(FleshStrike.GENERIC,FleshStrike.of(PowerForm.MUSCLE_MASS,WeaponMove.DEV_TENDRIL));
    }

    @Test void chargedStrikeOnAnimatedBodyRetainsSkinPointReceiverAndContactType(){
        var at=new Vec3(8,70,4);
        var contact=new HydraHurtContact.Contact("weak_2",at,new Vec3(-1,0,0),InfectedAttackStyle.PIERCE);
        var hit=contact.withStrike(FleshStrike.LASH,true);
        assertEquals("weak_2",hit.bone());
        assertEquals(at,hit.point());
        assertEquals(contact.normal(),hit.normal());
        assertEquals(InfectedImpactPayload.appearance(InfectedAttackStyle.PIERCE,FleshStrike.LASH,true),hit.appearance());
        assertEquals(InfectedAttackStyle.PIERCE.ordinal(),contact.appearance());
        assertEquals(InfectedAttackStyle.TEAR,FleshStrike.EDGE.style(WeaponCutContact.Kind.SLICE));
        assertEquals(InfectedAttackStyle.CRUSH,FleshStrike.EDGE.style(WeaponCutContact.Kind.BLUNT));
        assertEquals(InfectedAttackStyle.PIERCE,FleshStrike.LASH.style(WeaponCutContact.Kind.PUNCTURE));
    }
}
