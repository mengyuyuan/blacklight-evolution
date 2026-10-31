package com.codex.blacklight.firearms;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class BlockDamageLedgerTest {
    @Test public void fourRifleImpactsBreakTheSameWoodenBlock(){
        var ledger=new BlockDamageLedger(2,200);
        for(int i=0;i<3;i++)assertFalse(ledger.hit(1,10,4,16,i).broken());
        assertTrue(ledger.hit(1,10,4,16,3).broken());assertEquals(0,ledger.size());
        assertEquals(java.util.List.of(1L),ledger.drainCleared());
    }
    @Test public void replacementAndExpiryDoNotCarryOldDamage(){
        var ledger=new BlockDamageLedger(3,10);ledger.hit(1,10,12,16,0);
        assertFalse(ledger.hit(1,11,4,16,1).broken());
        assertFalse(ledger.hit(1,11,4,16,11).broken());
        ledger.expire(21);assertEquals(0,ledger.size());assertTrue(ledger.drainCleared().contains(1L));
    }
    @Test public void boundedEvictionClearsOldCracksAndInvalidNumbersAreIgnored(){
        var ledger=new BlockDamageLedger(1,200);ledger.hit(1,10,4,16,0);ledger.hit(2,10,4,16,1);
        assertEquals(1,ledger.size());assertEquals(java.util.List.of(1L),ledger.drainCleared());
        assertFalse(ledger.hit(3,10,Float.NaN,16,2).broken());assertEquals(1,ledger.size());
        assertFalse(ledger.hit(3,10,4,Float.POSITIVE_INFINITY,2).broken());assertEquals(1,ledger.size());
    }
    @Test public void clocksGoingBackCannotReplayOldDamage(){
        var ledger=new BlockDamageLedger(2,200);ledger.hit(1,10,12,16,100);
        assertFalse(ledger.hit(1,10,4,16,20).broken());
    }
}
