package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatMotionTest {
    @Test void rushDisplacementUsesReleaseTimeAndIntegratesFractionalTicksOnce(){
        var definition=LocalWeaponMotion.find(WeaponMove.BLADE_RUSH.id).orElseThrow();
        float release=definition.charge().orElseThrow().releaseTime();
        double sum=0;
        for(int tick=1;tick<=40;tick++){
            double from=com.codex.blacklight.animation.NativeRootMotion.weaponClock(tick-1,release,definition.transitionSeconds())-release;
            double to=com.codex.blacklight.animation.NativeRootMotion.weaponClock(tick,release,definition.transitionSeconds())-release;
            double step=CombatMotion.bladeRushStep(1,from,to);
            if(tick<4)assertEquals(0,step,1e-6,"anticipation must not advance the release callback");
            sum+=step;
        }
        assertEquals(8.7,sum,1e-5);
        assertEquals(6.09,CombatMotion.bladeRushStep(0,-1,3),1e-5);
        assertEquals(0,CombatMotion.bladeRushStep(1,.4,.3));
    }
    @Test void fallbackChargeStillChangesFlightSpeedWithoutPrivateData(){
        for(var move:new WeaponMove[]{WeaponMove.AIR_KICK,WeaponMove.AIR_PAIN})
            assertTrue(CombatMotion.speed(move,1)>CombatMotion.speed(move,0));
    }
    @Test void groundReleaseKeepsPerMoveStartDurationAndChargeRange(){
        var jab=CombatMotion.GroundStep.forMove(WeaponMove.MUSCLE_JAB);
        assertEquals(2F/30,jab.begin,1e-6);assertEquals(5F/30,jab.end,1e-6);
        assertEquals(.87,jab.advance(0,jab.end-jab.begin),1e-6);
        assertEquals(3*.87,jab.advance(1,jab.end-jab.begin),1e-6);
        assertSame(CombatMotion.GroundStep.forMove(WeaponMove.HAMMER_HOOK),CombatMotion.GroundStep.forMove(WeaponMove.HAMMER_HOOK_CHARGED));
        assertNull(CombatMotion.GroundStep.forMove(WeaponMove.WHIP_LONGSHOT));
    }
    @Test void sprintBladeBrakesAfterTheRushAndAcceptsPartialTicks(){
        double integrated=0;for(int t=0;t<40;t++)integrated+=CombatMotion.sprintBladeStep(.5F,t*.037,(t+1)*.037);
        assertEquals((8.5+7.5)*.87,integrated,1e-6);
        assertEquals(0,CombatMotion.sprintBladeStep(1,-1,.15),1e-6);
        assertEquals(0,CombatMotion.sprintBladeStep(1,2,1),1e-6);
    }
    @Test void longApproachHasEnoughTimeWithoutChangingContactReach(){
        for(var move:WeaponMove.values()){
            double speed=CombatMotion.speed(move,0);
            assertTrue(CombatMotion.travelBudget(160,speed)>160/speed);
            assertTrue(CombatMotion.travelBudget(10000,speed)<=640);
            assertTrue(CombatMotion.travelPose(move)<move.start/20F);
        }
    }
}
