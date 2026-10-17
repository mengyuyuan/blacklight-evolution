package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WingFlightPresentationTest {
    @Test void wakeTracksActualSpeedAndBoostAtRestDoesNotCreateAHighSpeedEffect(){
        var c=new WingFlightPresentation.Controller();
        for(int i=0;i<30;i++)c.update(i/20.,0,true,true);
        var rest=c.update(1.5,0,true,true);assertEquals(0,rest.flow());
        float peak=0;WingFlightPresentation.Frame frame=rest;
        for(int i=1;i<=20;i++){frame=c.update(1.5+i/20.,Math.min(1.25F,i*.095F),true,true);peak=Math.max(peak,frame.thrust());}
        assertTrue(peak>.65F);assertTrue(frame.flow()>.95F);
        for(int i=1;i<=20;i++)frame=c.update(2.5+i/20.,1.25F,true,true);
        assertTrue(frame.thrust()<.4F,"Sustained speed should settle after the initial power stroke");
        float braking=0;
        for(int i=1;i<=12;i++){frame=c.update(3.5+i/20.,Math.max(0,1.25F-i*.13F),true,false);braking=Math.max(braking,frame.brake());}
        assertTrue(braking>.65F);
        for(int i=1;i<=30;i++)frame=c.update(4.1+i/20.,0,false,false);
        assertTrue(frame.flow()<.001&&frame.thrust()<.001&&frame.brake()<.001);
    }
    @Test void duplicateFramesDoNotAdvanceAndReconnectDoesNotCreateAcceleration(){
        var c=new WingFlightPresentation.Controller();c.update(0,0,true,false);
        var a=c.update(.05,.1F,true,true);
        assertEquals(a,c.update(.05,1.25F,true,true));
        assertEquals(a,c.update(Double.NaN,1.25F,true,true));
        assertEquals(WingFlightPresentation.Frame.STILL,c.update(9,1.25F,true,true));
        var resumed=c.update(9.05,1.25F,true,true);assertTrue(resumed.thrust()<.2F);
    }
    @Test void speedBandsMeetContinuously(){
        for(float speed=0;speed<1.5;speed+=.001F){
            assertTrue(WingFlightPresentation.flow(speed+.001F)>=WingFlightPresentation.flow(speed));
            assertTrue(WingFlightPresentation.flow(speed+.001F)-WingFlightPresentation.flow(speed)<.002F);
        }
    }
    @Test void momentumAddsFollowThroughWithoutChangingHeadOrSeparatingShoulders(){
        var neutral=FlightAnimationClips.sample("boost",.3);var accelerating=neutral.copy();
        SovereignFlightMotion.followMomentum(accelerating,new WingFlightPresentation.Frame(1,1,0,1.2F),1,1);
        assertTrue(Math.abs(neutral.get("Root").rotation().dot(accelerating.get("Root").rotation()))<.9995F);
        assertEquals(neutral.get("Head").translation(),accelerating.get("Head").translation());
        for(String side:new String[]{"L","R"})assertEquals(neutral.get("Arm_"+side).translation(),accelerating.get("Arm_"+side).translation());
    }
}
