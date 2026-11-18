package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Queued source-policy checks; does not stand in for the entity lifecycle. */
class GreeneBattleHealthTest {
    @Test void downOwnsTheNextWaveButDoesNotRetroactivelyRefillThisArmour(){
        assertEquals(500,GreeneBattleHealth.segmentHealth(GreeneBattleHealth.INITIAL_WAVE,6000),.001);
        var down=GreeneBattleHealth.enterDown(1,6000);
        assertEquals(2,down.nextWaveCount());assertEquals(6000,down.initialStunnedHealth());
        assertEquals(700,GreeneBattleHealth.segmentHealth(down.nextWaveCount(),6000),.001);
        assertEquals(900,GreeneBattleHealth.segmentHealth(3,6000),.001);
        assertEquals(1000,GreeneBattleHealth.segmentHealth(4,6000),.001);
        assertEquals(1000,GreeneBattleHealth.segmentHealth(40,6000),.001);
        assertEquals(1400F/12,GreeneBattleHealth.segmentHealth(1,1400),.0001);
    }
    @Test void normalMissionSkipsTheOtherwiseReachableSecondStageFloor(){
        assertEquals(3930,GreeneBattleHealth.collapseFloor(1,6000),.001);
        assertEquals(-210,GreeneBattleHealth.collapseFloor(2,6000),.001);
        assertEquals(0,GreeneBattleHealth.collapseFloor(3,6000));
        assertEquals(0,GreeneBattleHealth.finalFloorDelayTicks(2));
        assertEquals(60,GreeneBattleHealth.finalFloorDelayTicks(3));
        assertEquals(60,GreeneBattleHealth.finalFloorDelayTicks(99));
        assertTrue(GreeneBattleHealth.releaseFloorOnEmergence(500,6000));
        assertFalse(GreeneBattleHealth.releaseFloorOnEmergence(501,6000));
    }
    @Test void stunUsesScaledStrictFiftyHpMarginAndSnapshotsBeforeItsDebit(){
        assertEquals(510,GreeneBattleHealth.stunDamage(6000,6000),.001);
        assertEquals(280,GreeneBattleHealth.stunDamage(560,6000),.001);
        assertEquals(510,GreeneBattleHealth.stunDamage(Math.nextUp(560F),6000),.001);
        assertEquals(119,GreeneBattleHealth.stunDamage(1400,1400),.001);
        assertEquals(50,GreeneBattleHealth.stunDamage(100,1400),.001);
        var down=GreeneBattleHealth.enterDown(1,6000);
        assertEquals(6000,down.initialStunnedHealth());
        assertFalse(GreeneBattleHealth.recoveryRequested(down.initialStunnedHealth(),5490,6000));
    }
    @Test void stunCountsTowardTheTwentySixPercentRequestWithoutMakingItAClamp(){
        assertFalse(GreeneBattleHealth.recoveryRequested(6000,4441,6000));
        assertTrue(GreeneBattleHealth.recoveryRequested(6000,4440,6000));
        assertTrue(GreeneBattleHealth.recoveryRequested(6000,4000,6000));
        assertFalse(GreeneBattleHealth.recoveryRequested(6000,0,6000));
        // The native floor permits the threshold-crossing hit to overshoot.
        assertEquals(400,GreeneBattleHealth.limitFinalDamage(4500,400,3930));
    }
    @Test void aPositiveFloorIsAppliedBeforeDeathWithoutCreatingNegativeDamage(){
        assertEquals(1070,GreeneBattleHealth.limitFinalDamage(5000,10000,3930));
        assertEquals(100,GreeneBattleHealth.limitFinalDamage(3000,100,3930),"actuallyHurt final health write owns a native floor above old health");
        assertEquals(10000,GreeneBattleHealth.limitFinalDamage(5000,10000,0));
        assertEquals(10000,GreeneBattleHealth.limitFinalDamage(5000,10000,-210));
        float allowed=GreeneBattleHealth.limitFinalDamage(1_000_000,2_000_000,.01F);
        assertTrue(1_000_000F-allowed>=.01F,"a positive floor smaller than a health ULP must not round to death");
    }
    @Test void neoPreservesAbsorptionBeforeApplyingTheSameHealthFloor(){
        assertEquals(45,GreeneBattleHealth.limitBeforeAbsorption(100,1000,25,80));
        assertEquals(20,GreeneBattleHealth.limitFinalDamage(100,975,80));
        assertEquals(20,GreeneBattleHealth.limitBeforeAbsorption(100,20,25,80));
        assertEquals(25,GreeneBattleHealth.limitBeforeAbsorption(80,1000,25,80));
        assertEquals(1000,GreeneBattleHealth.limitBeforeAbsorption(100,1000,25,0));
        assertEquals(1000,GreeneBattleHealth.limitBeforeAbsorption(50,1000,25,80),"higher floor must not manufacture a negative amount or skip the final write");
        float before=GreeneBattleHealth.limitBeforeAbsorption(1_000_000,4_000_000,1_000_000,.01F);
        float actual=before-Math.min(1_000_000F,before);
        assertTrue(1_000_000F-actual>=.01F);
    }
}
