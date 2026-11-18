package com.codex.blacklight.military.aircraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HelicopterAudioStateTest {
    @Test void joiningAFullSpeedAircraftDoesNotReplayStartup(){
        var state=new HelicopterAudioState(HelicopterAudioState.BLACKHAWK);
        assertEquals(HelicopterAudioState.Cue.NONE,state.tick(true,1,1,1,50).cue());
        assertEquals(HelicopterAudioState.Cue.WINDDOWN,state.tick(false,.98F,0,0,50).cue());
        assertEquals(HelicopterAudioState.Cue.NONE,state.tick(false,.96F,0,0,50).cue());
        assertEquals(HelicopterAudioState.Cue.STARTUP,state.tick(true,.5F,0,0,50).cue());
    }
    @Test void coldStartPlaysOnceAndBlackhawkCrossfadeUses2500Milliseconds(){
        var state=new HelicopterAudioState(HelicopterAudioState.BLACKHAWK);
        var mix=state.tick(true,0,0,0,50);assertEquals(HelicopterAudioState.Cue.STARTUP,mix.cue());
        for(int i=1;i<50;i++)mix=state.tick(true,1,0,0,50);
        assertEquals(.773809F,mix.rotorVolume(),1e-5);assertEquals(.660715F,mix.engineVolume(),1e-5);
        assertEquals(.446429F,mix.jetVolume(),1e-5);
        for(int i=0;i<51;i++)mix=state.tick(false,0,0,0,50);
        assertTrue(state.silent());assertEquals(0,mix.rotorVolume());
    }
    @Test void velocityAndThrottleControlDifferentPitchMaps(){
        var fast=new HelicopterAudioState(HelicopterAudioState.BLACKHAWK);
        var thrust=new HelicopterAudioState(HelicopterAudioState.BLACKHAWK);
        var a=fast.tick(true,1,1,0,10000);var b=thrust.tick(true,1,0,1,10000);
        assertEquals(1.183823F,a.rotorPitch(),1e-5);assertEquals(.772727F,a.enginePitch(),1e-5);
        assertEquals(.919118F,b.rotorPitch(),1e-5);assertEquals(1.096591F,b.enginePitch(),1e-5);
    }
    @Test void nativePitchSmoothingUsesDifferentLinearRiseAndFallRates(){
        assertEquals(.5+50.0/3500,HelicopterAudioState.smoothPitch(.5F,1,50),1e-6);
        assertEquals(1-50.0/7500,HelicopterAudioState.smoothPitch(1,.5F,50),1e-6);
        assertEquals(.8F,HelicopterAudioState.smoothPitch(.79F,.8F,50));
    }
    @Test void flightLoadComesFromAppliedMotionAndPower(){
        assertEquals(new HelicopterAudioState.Controls(0,0),HelicopterAudioState.controls(true,1,0,0,0));
        var cruise=HelicopterAudioState.controls(true,1,.65,0,0);assertEquals(1,cruise.velocity());assertEquals(1,cruise.throttle());
        var braking=HelicopterAudioState.controls(true,1,0,.035,0);assertEquals(0,braking.velocity());assertEquals(1,braking.throttle());
        assertEquals(new HelicopterAudioState.Controls(0,0),HelicopterAudioState.controls(false,1,.65,.035,.35));
        assertEquals(new HelicopterAudioState.Controls(0,0),HelicopterAudioState.controls(true,1,Double.NaN,0,0));
    }
    @Test void apacheKeepsItsOwnSourceBalanceAndFade(){
        var state=new HelicopterAudioState(HelicopterAudioState.APACHE);var mix=state.tick(true,1,1,1,2500);
        assertEquals(.375F,mix.rotorVolume(),1e-6);assertEquals(.1488095F,mix.engineVolume(),1e-6);
        mix=state.tick(true,1,1,1,5000);assertEquals(1.1397065F,mix.rotorPitch(),1e-6);assertEquals(.9943175F,mix.enginePitch(),1e-6);
    }
}
