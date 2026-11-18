package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterMissileFlightTest {
    private static final Vec3 FORWARD=new Vec3(1,0,0),TARGET=new Vec3(100,0,0);
    @Test void coldLaunchDropsBeforeIgnitionThenAcceleratesInsteadOfJumpingToCruise(){
        var flight=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,false);
        assertEquals(-.07,flight.step(Vec3.ZERO,TARGET,Vec3.ZERO).y,1e-12);
        assertEquals(-.14,flight.step(Vec3.ZERO,TARGET,Vec3.ZERO).y,1e-12);
        assertEquals(.125,flight.step(Vec3.ZERO,TARGET,Vec3.ZERO).x,1e-10);
        for(int i=0;i<30;i++)flight.step(Vec3.ZERO,TARGET,Vec3.ZERO);
        assertEquals(60,flight.velocity().length(),1e-9);
    }
    @Test void inheritedMotionSurvivesTheColdLaunchWithoutGenericMinecraftDrag(){
        var flight=new HelicopterMissileFlight(FORWARD,new Vec3(.2,.1,.3),false);
        var first=flight.step(Vec3.ZERO,TARGET,Vec3.ZERO);
        assertEquals(.2,first.x,1e-12);assertEquals(.03,first.y,1e-12);assertEquals(.3,first.z,1e-12);
        flight.step(Vec3.ZERO,TARGET,Vec3.ZERO);
        assertEquals(new Vec3(.2,.1,.3).length()*20,flight.velocity().length(),1e-10);
    }
    @Test void trackingStartsAtHalfSecondWithSpeedScaledTurnAndPreservedSidewaysMotion(){
        var flight=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,false);var target=new Vec3(40,0,30);
        for(int i=0;i<9;i++){flight.step(Vec3.ZERO,target,Vec3.ZERO);assertEquals(FORWARD,flight.heading());}
        double before=flight.velocity().length();flight.step(Vec3.ZERO,target,Vec3.ZERO);
        assertEquals(6*.05*before/60,Math.acos(FORWARD.dot(flight.heading())),1e-9);
        assertTrue(flight.velocity().cross(flight.heading()).length()>.01,"turning cannot teleport velocity onto heading");
    }
    @Test void interceptPredictsACrossingTargetAndDoesNotInventAReachableRecedingSolution(){
        var lead=HelicopterMissileFlight.lead(Vec3.ZERO,new Vec3(30,0,0),new Vec3(0,0,10),60);
        assertEquals(30,lead.x);assertTrue(lead.z>5&&lead.z<5.1);
        assertEquals(TARGET,HelicopterMissileFlight.lead(Vec3.ZERO,TARGET,new Vec3(100,0,0),60));
    }
    @Test void completeFlightReachesTheMovedTargetOutsideTheOriginalFiringRay(){
        var flight=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,false);Vec3 position=Vec3.ZERO;double nearest=100;
        for(int i=0;i<70;i++){
            var target=new Vec3(40,0,i*.3);position=position.add(flight.step(position,target,new Vec3(0,0,6)));
            nearest=Math.min(nearest,position.distanceTo(target));
        }
        assertTrue(nearest<1,"actual curved interception must reach the moving target: "+nearest);
    }
    @Test void rearTargetBreaksLockPermanentlyAndDoesNotAllowAUturn(){
        var flight=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,false);
        for(int i=0;i<12;i++)flight.step(Vec3.ZERO,TARGET,Vec3.ZERO);
        flight.step(Vec3.ZERO,new Vec3(-10,0,0),Vec3.ZERO);assertTrue(flight.lost());var heading=flight.heading();
        for(int i=0;i<10;i++)flight.step(Vec3.ZERO,new Vec3(20,0,20),Vec3.ZERO);
        assertEquals(heading,flight.heading());
    }
    @Test void persistedFlightContinuesTheSameTrajectoryWithoutRestartingIgnitionOrFriction(){
        var first=new HelicopterMissileFlight(FORWARD,new Vec3(.2,0,.1),true);Vec3 position=Vec3.ZERO,target=new Vec3(100,4,20);
        for(int i=0;i<14;i++)position=position.add(first.step(position,target,new Vec3(0,0,3)));
        var second=HelicopterMissileFlight.load(first.save());assertNotNull(second);assertTrue(second.player());
        for(int i=0;i<40;i++){Vec3 a=first.step(position,target,new Vec3(0,0,3)),b=second.step(position,target,new Vec3(0,0,3));assertEquals(a,b);assertEquals(first.heading(),second.heading());position=position.add(a);}
    }
    @Test void lifetimeAndMalformedStateCannotProduceImmortalOrNonfiniteMissiles(){
        var flight=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,false);
        for(int i=0;i<200;i++)flight.step(Vec3.ZERO,TARGET,Vec3.ZERO);
        assertTrue(flight.expired());assertEquals(Vec3.ZERO,flight.step(Vec3.ZERO,TARGET,Vec3.ZERO));
        var saved=flight.save();saved.putDouble("VelocityX",Double.NaN);assertNull(HelicopterMissileFlight.load(saved));
        saved=flight.save();saved.putInt("Age",-1);assertNull(HelicopterMissileFlight.load(saved));
        saved=flight.save();saved.remove("ColdZ");assertNull(HelicopterMissileFlight.load(saved));
    }
    @Test void playerProfileTurnsFasterThanAiWithoutChangingTheSpeedLimit(){
        var ai=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,false);var player=new HelicopterMissileFlight(FORWARD,Vec3.ZERO,true);
        for(int i=0;i<10;i++){ai.step(Vec3.ZERO,new Vec3(40,0,40),Vec3.ZERO);player.step(Vec3.ZERO,new Vec3(40,0,40),Vec3.ZERO);}
        assertTrue(player.heading().z>ai.heading().z);assertTrue(player.velocity().length()<=60);
    }
}
