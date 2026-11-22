package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GoddessDamageBudgetTest {
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
