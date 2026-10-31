package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedPoiseTest {
    @Test void repeatedHitsCannotExtendTheWindowOrRemoveTimeToCounterattack(){
        var poise=new EvolvedPoise();assertTrue(poise.accept(100));
        for(int now=101;now<164;now++)assertFalse(poise.accept(now),"spam cannot move the recovery boundary");
        assertFalse(poise.protectedAt(164));assertTrue(poise.accept(164));
        assertTrue(EvolvedPoise.FREE_ACTION_TICKS>4+16,"recovery plus first real contact fits before control can recur");
    }
    @Test void aFreshControllerDoesNotInheritAnUnloadedCombatLock(){
        assertFalse(new EvolvedPoise().protectedAt(0));assertTrue(new EvolvedPoise().accept(0));
    }
}
