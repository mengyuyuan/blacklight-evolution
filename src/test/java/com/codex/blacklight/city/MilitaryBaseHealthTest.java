package com.codex.blacklight.city;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MilitaryBaseHealthTest {
    @Test void oneRealSurvivingTerminalRestoresOperationalStateImmediately(){
        var health=new MilitaryBaseHealth(true);
        assertTrue(health.observe(100,true,true));assertTrue(health.operational());assertFalse(health.inactive());
        assertFalse(health.observe(110,false,true));assertTrue(health.operational());
    }
    @Test void allMissingCoresPauseIssuanceBeforeTheyAreConfirmedDestroyed(){
        var health=new MilitaryBaseHealth(false);health.observe(100,true,true);
        health.observe(120,true,false);assertFalse(health.operational());assertFalse(health.inactive());
        health.observe(319,true,false);assertFalse(health.inactive());
        assertTrue(health.observe(320,true,false));assertTrue(health.inactive());
    }
    @Test void anUnknownIntervalBreaksTheRequiredContinuousLoadedObservation(){
        var health=new MilitaryBaseHealth(false);health.observe(100,true,false);health.observe(280,true,false);
        health.observe(290,false,false);assertEquals("UNKNOWN",health.label());
        health.observe(2000,false,false);assertFalse(health.inactive());
        health.observe(2020,true,false);assertFalse(health.inactive());
        health.observe(2219,true,false);assertFalse(health.inactive());
        health.observe(2220,true,false);assertTrue(health.inactive());
    }
    @Test void aRepairedCoreResetsThePreviousMissingInterval(){
        var health=new MilitaryBaseHealth(false);health.observe(100,true,false);health.observe(280,true,false);
        health.observe(290,true,true);health.observe(300,true,false);health.observe(480,true,false);assertFalse(health.inactive());
        health.observe(500,true,false);assertTrue(health.inactive());
    }
    @Test void savedFailureRemainsFailedWhileItsChunksAreUnknown(){
        var health=new MilitaryBaseHealth(true);health.observe(400,false,false);
        assertTrue(health.inactive());assertFalse(health.operational());assertEquals("INACTIVE_UNLOADED",health.label());
        health.observe(420,true,false);assertTrue(health.inactive());
    }
    @Test void rollingBackWorldTimeCannotCompleteAnOldObservation(){
        var health=new MilitaryBaseHealth(false);health.observe(1000,true,false);health.observe(100,true,false);
        health.observe(299,true,false);assertFalse(health.inactive());health.observe(300,true,false);assertTrue(health.inactive());
    }
}
