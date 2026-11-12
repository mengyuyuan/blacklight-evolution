package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponStrokeTimingTest {
    @Test void bladeRushKeepsThreeCuttingArcsButOneDamageLedger(){
        var move=WeaponMove.BLADE_RUSH;
        assertEquals(0,move.cutIndex(4));assertEquals(1,move.cutIndex(7));assertEquals(2,move.cutIndex(12));
        assertEquals(0,move.contactIndex(4));assertEquals(0,move.contactIndex(7));assertEquals(0,move.contactIndex(12));
        assertEquals(-1,move.cutIndex(5));assertEquals(-1,move.cutIndex(10));
        assertEquals(-1,move.cutIndex(move.end));
    }
    @Test void ordinaryClawSwingDoesNotAcquireAnotherArcInRecovery(){
        var move=WeaponMove.CLAW_AUTO1;
        for(int t=move.start;t<move.end;t++)assertEquals(0,move.cutIndex(t));
        assertEquals(-1,move.cutIndex(move.end));assertEquals(-1,move.cutIndex(move.start-1));
    }
}
