package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneMissileFlightTest {
    private static final Vec3 EAST=new Vec3(1,0,0);
    @Test void unguidedProfilesReachTheirConfiguredSpeedOnFirstUpdate(){
        for(var ammo:new GreeneMissileFlight.Ammo[]{GreeneMissileFlight.Ammo.RIFLE,GreeneMissileFlight.Ammo.SHOTGUN,GreeneMissileFlight.Ammo.ANTI_TANK,GreeneMissileFlight.Ammo.ANTI_AIR}){
            var flight=new GreeneMissileFlight(ammo,EAST,Vec3.ZERO);
            var move=flight.step(Vec3.ZERO,new Vec3(0,0,100),Vec3.ZERO,.05);
            assertEquals(ammo.speed*.05,move.x,1e-8);assertEquals(0,move.y);assertEquals(0,move.z);
        }
    }
    @Test void inheritedDriftIsNotErasedByForwardSpeedCorrection(){
        var flight=new GreeneMissileFlight(GreeneMissileFlight.Ammo.RIFLE,EAST,new Vec3(0,0,8));
        assertEquals(.4,flight.step(Vec3.ZERO,null,null,.05).z,1e-8);
        Vec3 actualHeading=new Vec3(60,0,8).normalize();
        assertTrue(flight.heading().distanceTo(actualHeading)<1e-8);
        Vec3 next=flight.step(Vec3.ZERO,null,null,.05);
        assertTrue(next.distanceTo(actualHeading.scale(60*.05))<1e-8,"next speed correction follows real inherited drift");
    }
    @Test void sourceDisplacementEpsilonDoesNotUseVanillaNormalizeCutoff(){
        var barelyMoving=new GreeneMissileFlight(GreeneMissileFlight.Ammo.RIFLE,EAST,new Vec3(0,0,8));
        Vec3 delta=barelyMoving.step(Vec3.ZERO,null,null,3e-7);
        assertTrue(delta.length()<1e-4&&delta.x>1e-5);
        assertEquals(1,barelyMoving.heading().length(),1e-8);
        assertTrue(barelyMoving.heading().z>0,"native normalizes above its per-axis 1e-5 threshold");
        var tiny=new GreeneMissileFlight(GreeneMissileFlight.Ammo.RIFLE,EAST,new Vec3(0,0,8));
        tiny.step(Vec3.ZERO,null,null,1e-8);assertEquals(EAST,tiny.heading());
    }
    @Test void deathFlowerBrakesBeforeItsPoweredAcceleration(){
        var flight=new GreeneMissileFlight(GreeneMissileFlight.Ammo.DEATH_FLOWER,EAST,Vec3.ZERO);
        flight.step(Vec3.ZERO,null,null,.05);assertEquals(11.8,flight.velocity().x,1e-8);assertFalse(flight.brakeComplete());
        for(int i=1;i<60;i++)flight.step(Vec3.ZERO,null,null,.05);
        assertTrue(flight.brakeComplete());assertEquals(0,flight.velocity().length(),1e-8);
        flight.step(Vec3.ZERO,null,null,.05);assertEquals(.2,flight.velocity().x,1e-8);
    }
    @Test void guidanceStartsAfterDelayAndPreservesEachTurnRate(){
        for(var ammo:new GreeneMissileFlight.Ammo[]{GreeneMissileFlight.Ammo.DEATH_FLOWER,GreeneMissileFlight.Ammo.INSECT_SWARM}){
            var flight=new GreeneMissileFlight(ammo,EAST,Vec3.ZERO);var target=new Vec3(0,0,100);
            for(int i=0;i<19;i++)flight.step(Vec3.ZERO,target,Vec3.ZERO,.05);
            assertEquals(EAST,flight.heading());flight.step(Vec3.ZERO,target,Vec3.ZERO,.05);
            double turn=Math.acos(flight.heading().dot(EAST));
            assertTrue(turn>0&&turn<=ammo.turnRate*.05+1e-8,"post-step heading follows acceleration, bounded by guidance turn");
            assertTrue(flight.heading().distanceTo(flight.velocity().normalize())<1e-8);
        }
    }
    @Test void perfectTrackingStillLeadsButIgnoresShotError(){
        var flight=new GreeneMissileFlight(GreeneMissileFlight.Ammo.INSECT_SWARM,EAST,Vec3.ZERO);
        flight.step(Vec3.ZERO,new Vec3(100,0,0),new Vec3(0,0,2),new Vec3(0,0,-100),1);
        assertTrue(flight.heading().z>0,"The source perfect flag suppresses error, not user dead reckoning");
    }
    @Test void sourceBoundsUseStrictComparisons(){
        var flight=new GreeneMissileFlight(GreeneMissileFlight.Ammo.INSECT_SWARM,EAST,Vec3.ZERO);
        assertFalse(flight.proximityTriggered(Vec3.ZERO,EAST));assertTrue(flight.proximityTriggered(Vec3.ZERO,EAST.scale(.999)));
        assertFalse(flight.outOfRange(Vec3.ZERO,EAST.scale(175)));assertTrue(flight.outOfRange(Vec3.ZERO,EAST.scale(175.001)));
        flight.step(Vec3.ZERO,null,null,5);assertFalse(flight.expired());assertEquals(Vec3.ZERO,flight.step(Vec3.ZERO,null,null,.001));assertTrue(flight.expired());
    }
    @Test void persistenceKeepsTheBrakeAndGuidanceState(){
        var flight=new GreeneMissileFlight(GreeneMissileFlight.Ammo.DEATH_FLOWER,EAST,new Vec3(0,0,2));
        for(int i=0;i<24;i++)flight.step(Vec3.ZERO,new Vec3(0,0,100),Vec3.ZERO,.05);
        flight.loseTarget();var saved=flight.save();var loaded=GreeneMissileFlight.load(saved);assertNotNull(loaded);
        assertTrue(loaded.lostGuidance());assertEquals(flight.ageSeconds(),loaded.ageSeconds());
        assertEquals(flight.step(Vec3.ZERO,null,null,.05),loaded.step(Vec3.ZERO,null,null,.05));
        saved.putDouble("Friction",Double.NaN);assertNull(GreeneMissileFlight.load(saved));
    }
}
