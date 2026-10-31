package com.codex.blacklight.movement;

import com.codex.blacklight.power.*;
import com.codex.blacklight.state.BlacklightState;
import com.codex.blacklight.animation.WingFlightPresentation;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SupersonicFlightTest {
    @Test void packetBudgetCoversBatchesButDoesNotExemptFlightFromSpeedValidation(){
        for(int count=1;count<=5;count++){
            double expected=22*count,allowed=WingFlightTuning.packetAllowance(100,22,3,count)*count+22*22;
            assertTrue(expected*expected<=allowed);
            assertTrue(Math.pow(expected+8,2)>allowed,"An extra eight-block teleport must still fail the budget");
        }
        assertEquals(100,WingFlightTuning.packetAllowance(100,22,0,5));
        assertEquals(WingFlightTuning.packetAllowance(100,22,3,1),WingFlightTuning.packetAllowance(100,22,3,1000));
        assertEquals(100,WingFlightTuning.packetAllowance(100,Double.NaN,3,2));
    }
    private WingFlightPhysics.Velocity step(WingFlightPhysics.Velocity v,int tier,int forward,boolean boost,float yaw){
        return WingFlightPhysics.step(v.x(),v.y(),v.z(),forward,0,false,false,boost,yaw,0,false,tier);
    }
    private double speed(WingFlightPhysics.Velocity v){return Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z());}
    @Test void upgradesRequireWingsAndPreviousTiersAndSurviveCodec(){
        var s=new BlacklightState();s.infect();s.addEvolutionPoints(10000);
        assertFalse(s.buyMovementUpgrade(MovementUpgrade.WING_SPEED_1));assertEquals(10000,s.getEvolutionPoints());
        s.unlock(PowerForm.SOVEREIGN_WINGS);
        assertFalse(s.buyMovementUpgrade(MovementUpgrade.WING_SPEED_3));
        for(var u:new MovementUpgrade[]{MovementUpgrade.WING_SPEED_1,MovementUpgrade.WING_SPEED_2,MovementUpgrade.WING_SPEED_3}){
            assertTrue(s.buyMovementUpgrade(u));assertFalse(s.buyMovementUpgrade(u));
        }
        assertEquals(5800,s.getEvolutionPoints());
        var json=BlacklightState.CODEC.encodeStart(JsonOps.INSTANCE,s).getOrThrow(false, message -> { throw new AssertionError(message); });
        var restored=BlacklightState.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertEquals(3,MovementUpgrade.wingTier(restored.getMovementUpgradeMask()));
        assertEquals(0,MovementUpgrade.wingTier(MovementUpgrade.WING_SPEED_3.bit()));
    }
    @Test void tiersRampContinuouslyToTheirOwnCapsAndOnlyLastCrossesSoundBarrier(){
        for(int tier=0;tier<=3;tier++){
            var tuning=WingFlightTuning.tier(tier);var v=new WingFlightPhysics.Velocity(0,0,0);
            int crossed=-1;
            for(int tick=0;tick<250;tick++){
                var next=step(v,tier,1,true,0);
                assertTrue(speed(next)-speed(v)<=tuning.acceleration()+1e-8);
                if(speed(next)>=WingFlightTuning.SONIC_SPEED&&crossed<0)crossed=tick;
                v=next;
            }
            assertEquals(tuning.boostSpeed(),speed(v),1e-6);
            if(tier<3)assertEquals(-1,crossed);else assertTrue(crossed>40&&crossed<80);
            for(int i=0;i<100;i++)v=step(v,tier,0,false,0);
            assertEquals(0,speed(v),1e-6);
        }
    }
    @Test void ordinaryControlsAndTakeoffDoNotInstantlyBecomeSupersonic(){
        var v=new WingFlightPhysics.Velocity(0,0,0);
        for(int i=0;i<200;i++)v=step(v,3,1,false,0);
        assertEquals(.52,speed(v),1e-6);
        v=new WingFlightPhysics.Velocity(0,0,0);
        for(int i=0;i<200;i++)v=WingFlightPhysics.step(v.x(),v.y(),v.z(),-1,0,false,false,true,0,0,false,3);
        assertEquals(1.25,speed(v),1e-6);
    }
    @Test void unloadedCorridorIsApproachedWithoutCrossingItsBoundary(){
        double position=0,velocity=22;
        for(int i=0;i<150;i++){
            double origin=position;
            velocity=Math.min(22,velocity+.32);
            velocity=WingFlightCorridor.limit(velocity,2,d->origin+d<180);
            position+=velocity;assertTrue(position<180);
        }
        assertEquals(0,velocity,1e-6);
        assertTrue(position>170);
        assertEquals(22,WingFlightCorridor.limit(22,2,d->true));
        assertEquals(0,WingFlightCorridor.limit(22,2,d->false));
    }
    private double loadingRoute(double loadRate){
        var budget=new WingFlightCorridor.LoadBudget();double position=0,velocity=0;
        for(int tick=0;tick<600;tick++){
            // Model chunks arriving in batches, not an artificially continuous frontier.
            double edge=96+Math.floor(tick*loadRate/16)*16,origin=position;
            velocity=WingFlightCorridor.adaptiveLimit(Math.min(22,velocity+.32),3,tick,budget,d->origin+d<edge);
            position+=velocity;assertTrue(position<edge,"Must stay inside the loaded frontier");
        }
        return position/600;
    }
    @Test void realChunkThroughputSetsActualFlightSpeed(){
        double slow=loadingRoute(2),medium=loadingRoute(6),fast=loadingRoute(24);
        assertTrue(slow<2.2);assertTrue(medium>slow*2&&medium<6.2);
        assertTrue(fast>20&&fast>medium*3);
    }
    @Test void newChunkBatchRecoversSpeedGraduallyAndSameTickIsIdempotent(){
        var budget=new WingFlightCorridor.LoadBudget();var tier=WingFlightTuning.tier(3);
        double before=budget.update(100,34,false,tier);assertEquals(4,before);
        double after=budget.update(101,200,true,tier);
        assertEquals(before+tier.acceleration()*.5,after,1e-9);
        assertEquals(after,budget.update(101,200,true,tier));
        assertEquals(0,WingFlightCorridor.adaptiveLimit(22,3,102,budget,d->false));
    }
    @Test void adaptiveBudgetBrakesForLostChunksAndRestoresTheTierCap(){
        var budget=new WingFlightCorridor.LoadBudget();var tier=WingFlightTuning.tier(3);
        double last=budget.update(0,200,true,tier);assertEquals(22,last);
        for(int tick=1;tick<20;tick++){
            double next=budget.update(tick,34,false,tier);
            assertTrue(next<=last);assertTrue(last-next<=tier.braking()*.35+1e-8);last=next;
        }
        for(int tick=20;tick<200;tick++)last=budget.update(tick,200,true,tier);
        assertEquals(22,last);
    }
    @Test void shockIsOncePerCrossingWithHysteresisAndNoGroundBoom(){
        var gate=new WingFlightTuning.SonicGate();
        assertFalse(gate.update(0,22,false));assertFalse(gate.update(1,16,true));
        assertTrue(gate.update(2,17.2,true));
        for(int i=3;i<70;i++)assertFalse(gate.update(i,i%2==0?17:18,true));
        assertFalse(gate.update(70,14,true));assertTrue(gate.update(71,18,true));
    }
    @Test void presentationPreservesSupersonicVelocityAndSettlesWithoutFalseReset(){
        var c=new WingFlightPresentation.Controller();c.update(0,0,true,true);
        WingFlightPresentation.Frame frame=null;
        for(int i=1;i<140;i++)frame=c.update(i*.05,Math.min(22,i*.32F),true,true);
        assertTrue(frame.mach()>1.27);assertEquals(1,frame.streamline(),.001);
        for(int i=140;i<220;i++)frame=c.update(i*.05,0,true,false);
        assertEquals(0,frame.streamline(),.001);
    }
}
