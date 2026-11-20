package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollImpactTimingTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraft(){
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void lethalContactsKeepTheWeaponPauseWhenTheLivingHoldHasAlreadyEnded(){
        assertEquals(1,HitStop.ragdollFreezeTicks(false,true,0,ImpactTier.LIGHT.hitstop));
        assertEquals(4,HitStop.ragdollFreezeTicks(false,true,0,ImpactTier.HEAVY.hitstop));
        assertEquals(6,HitStop.ragdollFreezeTicks(false,true,0,ImpactTier.FINISHER.hitstop));
        assertEquals(8,HitStop.ragdollFreezeTicks(false,false,8,ImpactTier.HEAVY.hitstop));
    }
    @Test void LaterEdgesCannotRestartHitstopAndCosmeticHoldsRemainBounded(){
        assertEquals(0,HitStop.ragdollFreezeTicks(true,true,8,8));
        assertEquals(0,HitStop.ragdollFreezeTicks(true,false,8,8));
        assertEquals(8,HitStop.ragdollFreezeTicks(false,true,0,100));
        assertEquals(0,HitStop.ragdollFreezeTicks(false,true,0,-2));
    }
}
