package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvolvedBalanceTest {
    @Test void superEliteBudgetUsesPlayerHealthWithoutMultiplyingSmallHits(){
        var r=EvolvedBalance.Rules.DEFAULT;
        assertEquals(6,EvolvedBalance.playerDamage(40,100,5,1,r),.001);
        assertEquals(40,EvolvedBalance.playerDamage(40,1000,5,1,r),.001);
        assertEquals(60,EvolvedBalance.playerDamage(400,1000,5,1,r),.001);
        assertEquals(2,EvolvedBalance.playerDamage(2,100,5,1,r),.001);
        assertEquals(9,EvolvedBalance.playerDamage(40,100,5,1.5F,r),.001);
        assertEquals(0,EvolvedBalance.playerDamage(Float.NaN,100,5,1,r));
    }
}
