package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GoddessDamageBudgetTest {
    @Test void lateProjectileRetainsItsCastBudgetAfterTheNextGestureStarts(){
        var budget = new GoddessDamageBudget();
        var boss = UUID.randomUUID();
        assertEquals(30,budget.debit(boss,1,-1,10,100,100),.001);
        assertEquals(15,budget.debit(boss,3,1,20,100,100),.001);
        assertEquals(15,budget.debit(boss,1,3,80,100,100),.001);
        assertEquals(0,budget.debit(boss,1,3,145,100,100),.001);
        assertEquals(30,budget.debit(boss,3,1,146,100,100),.001);
    }

    @Test void fullHealthKeepsPressureAndLowHealthAdvancesTheFeedingWindow(){
        assertFalse(GoddessAttacks.recoveryDue(4,100,100));
        assertFalse(GoddessAttacks.recoveryDue(3,80,100));
        assertTrue(GoddessAttacks.recoveryDue(4,80,100));
        assertTrue(GoddessAttacks.recoveryDue(2,50,100));
        assertFalse(GoddessAttacks.recoveryDue(1,50,100));
    }

    @Test void multipleSourcesAndComboBoundariesStillShareTheSlidingCap(){
        var budget=new GoddessDamageBudget();
        var boss=UUID.randomUUID();
        assertEquals(600,budget.debit(boss,1,59,2000,8000),.001);
        assertEquals(300,budget.debit(boss,2,60,2000,900),.001);
        assertEquals(0,budget.debit(boss,3,118,2000,900),.001);
        assertEquals(600,budget.debit(boss,4,119,2000,900),.001);
        assertEquals(300,budget.debit(boss,4,180,2000,900),.001);
        assertEquals(0,budget.debit(boss,4,250,2000,900),.001);
    }
}
