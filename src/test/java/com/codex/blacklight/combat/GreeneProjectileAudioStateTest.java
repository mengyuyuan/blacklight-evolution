package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.combat.GreeneProjectileAudioState.Action.*;

class GreeneProjectileAudioStateTest {
    @Test void polledHeartbeatDoesNotRetriggerAndImpactStopsBiomass(){
        var s=new GreeneProjectileAudioState(GreeneProjectileAudioState.BIOMASS);
        assertEquals(START,s.tick(true,false,false,true,false));
        for(int i=0;i<80;i++)assertEquals(KEEP,s.tick(true,false,false,true,true));
        assertEquals(STOP,s.tick(true,true,false,true,true));assertTrue(s.dead());
        assertEquals(IDLE,s.tick(true,false,false,true,false));
    }
    @Test void fireUsesPatchFadeAndNeverReacquiresAfterFinish(){
        var s=new GreeneProjectileAudioState(GreeneProjectileAudioState.FIRE);
        for(int i=0;i<10;i++)s.tick(true,false,false,true,i>0);
        assertEquals(1,s.envelope(),1e-6);s.tick(true,true,false,true,true);
        assertEquals(.95,s.envelope(),1e-6);
        for(int i=0;i<18;i++)assertEquals(KEEP,s.tick(true,true,false,true,true));
        assertEquals(STOP,s.tick(true,true,false,true,true));assertTrue(s.dead());
    }
    @Test void pauseFreezesHandlesFadeAndMissingVoiceBackoff(){
        var s=new GreeneProjectileAudioState(GreeneProjectileAudioState.FIRE);
        assertEquals(START,s.tick(true,false,false,true,false));float before=s.envelope();
        for(int i=0;i<200;i++)assertEquals(KEEP,s.tick(true,false,true,true,false));
        assertEquals(before,s.envelope());
        for(int i=0;i<19;i++)assertEquals(KEEP,s.tick(true,false,false,true,false));
        assertEquals(START,s.tick(true,false,false,true,false));
    }
    @Test void removeOrWorldChangeStopsEvenWhilePausedAndRetrackingStartsFresh(){
        var s=new GreeneProjectileAudioState(GreeneProjectileAudioState.FIRE);s.tick(true,false,false,true,false);
        assertEquals(STOP,s.tick(false,false,true,true,true));assertTrue(s.dead());
        var retracked=new GreeneProjectileAudioState(GreeneProjectileAudioState.FIRE);
        assertEquals(START,retracked.tick(true,false,false,true,false));
        assertEquals(IDLE,new GreeneProjectileAudioState(GreeneProjectileAudioState.FIRE).tick(true,true,false,true,false));
    }
    @Test void muteStopsWithoutRetryStormAndUnmuteResumesOnlyAnActiveProjectile(){
        var s=new GreeneProjectileAudioState(GreeneProjectileAudioState.BIOMASS);s.tick(true,false,false,true,false);
        assertEquals(STOP,s.tick(true,false,false,false,true));
        for(int i=0;i<200;i++)assertEquals(IDLE,s.tick(true,false,false,false,false));
        assertEquals(START,s.tick(true,false,false,true,false));
    }
    @Test void nativeDefaultSentinelsRetainPatchPitchAndDistance(){
        var mother=new GreeneProjectileAudioState(GreeneProjectileAudioState.BIOMASS);
        assertEquals(.9,mother.pitch(0),1e-6);assertEquals(1.1,mother.pitch(1),1e-6);
        assertEquals(GreeneProjectileAudioState.MIX_GAIN,mother.gain(10),1e-6);assertEquals(0,mother.gain(55));
        var fire=new GreeneProjectileAudioState(GreeneProjectileAudioState.FIRE);
        assertEquals(.8,fire.pitch(0),1e-6);assertEquals(1.2,fire.pitch(1),1e-6);
        assertEquals(2,fire.profile().minimumDistance());assertEquals(25,fire.profile().maximumDistance());
        assertEquals(1.404762,fire.profile().sourceVolume(),1e-6);
    }
}
