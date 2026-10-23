package com.codex.blacklight.movement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WingFlightPhysicsTest {
    @Test void acceleratesWithoutTeleportingThenBrakesToHover(){
        var v=new WingFlightPhysics.Velocity(0,0,0);
        for(int i=0;i<100;i++){
            var next=WingFlightPhysics.step(v.x(),v.y(),v.z(),1,0,false,false,true,0,0,false);
            assertTrue(Math.abs(next.z()-v.z())<=.095001);v=next;
        }
        assertEquals(1.25,v.z(),1e-6);
        for(int i=0;i<20;i++)v=WingFlightPhysics.step(v.x(),v.y(),v.z(),0,0,false,false,false,0,0,false);
        assertEquals(0,v.z(),1e-6);assertEquals(0,v.y(),1e-6);
    }
    @Test void diagonalsDoNotGainExtraSpeedAndPitchChangesAltitude(){
        var v=new WingFlightPhysics.Velocity(0,0,0);
        for(int i=0;i<100;i++)v=WingFlightPhysics.step(v.x(),v.y(),v.z(),1,1,true,false,true,90,-40,false);
        assertEquals(1.25,Math.sqrt(v.x()*v.x()+v.y()*v.y()+v.z()*v.z()),1e-6);
        assertTrue(v.y()>0);
    }
    @Test void nonFiniteInputsCannotPoisonMovement(){
        var v=WingFlightPhysics.step(Double.NaN,Double.POSITIVE_INFINITY,0,Integer.MAX_VALUE,Integer.MIN_VALUE,false,false,true,Float.NaN,Float.POSITIVE_INFINITY,false);
        assertTrue(Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z()));
    }
    @Test void takeoffAndDescentHaveOppositeVerticalDirections(){
        assertTrue(WingFlightPhysics.step(0,0,0,0,0,false,false,false,0,0,true).y()>0);
        assertTrue(WingFlightPhysics.step(0,0,0,0,0,false,true,false,0,0,false).y()<0);
    }
    @Test void boostCanReverseOrTurnWithoutReleasingForward(){
        for(float yaw:new float[]{90,180,-90}){
            var v=new WingFlightPhysics.Velocity(0,0,1.25);
            for(int tick=0;tick<18;tick++)v=WingFlightPhysics.step(v.x(),v.y(),v.z(),1,0,false,false,true,yaw,0,false);
            double dot=-Math.sin(Math.toRadians(yaw))*v.x()+Math.cos(Math.toRadians(yaw))*v.z();
            assertTrue(dot>1.15,"Held forward must follow a changed camera yaw: "+yaw);
        }
    }
    @Test void personalSpeedCannotRaiseUnlockedLimitAndLoweringBrakesExistingMomentum(){
        for(int tier=0;tier<=3;tier++)for(double scale:new double[]{.1,.25,.5,1,5,Double.NaN}){
            var v=new WingFlightPhysics.Velocity(0,0,0);
            for(int tick=0;tick<600;tick++)v=WingFlightPhysics.step(v.x(),v.y(),v.z(),1,0,false,false,true,0,0,false,tier,scale);
            double expected=WingFlightTuning.tier(tier).boostSpeed()*(Double.isFinite(scale)?Math.max(.1,Math.min(1,scale)):1);
            assertEquals(expected,v.z(),1e-6);
        }
        var v=new WingFlightPhysics.Velocity(0,0,22);
        for(int tick=0;tick<60;tick++)v=WingFlightPhysics.step(v.x(),v.y(),v.z(),1,0,false,false,true,0,0,false,3,.1);
        assertEquals(2.2,v.z(),1e-6);
    }
}
