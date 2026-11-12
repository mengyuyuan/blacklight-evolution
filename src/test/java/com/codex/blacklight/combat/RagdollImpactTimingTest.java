package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollImpactTimingTest {
    @Test void lethalContactsKeepTheWeaponPauseWhenTheLivingHoldHasAlreadyEnded(){
        assertEquals(1,RagdollImpact.freezeTicks(false,true,0,ImpactTier.LIGHT.hitstop));
        assertEquals(4,RagdollImpact.freezeTicks(false,true,0,ImpactTier.HEAVY.hitstop));
        assertEquals(6,RagdollImpact.freezeTicks(false,true,0,ImpactTier.FINISHER.hitstop));
        assertEquals(8,RagdollImpact.freezeTicks(false,false,8,ImpactTier.HEAVY.hitstop));
    }
    @Test void LaterEdgesCannotRestartHitstopAndCosmeticHoldsRemainBounded(){
        assertEquals(0,RagdollImpact.freezeTicks(true,true,8,8));
        assertEquals(0,RagdollImpact.freezeTicks(true,false,8,8));
        assertEquals(8,RagdollImpact.freezeTicks(false,true,0,100));
        assertEquals(0,RagdollImpact.freezeTicks(false,true,0,-2));
    }
}
