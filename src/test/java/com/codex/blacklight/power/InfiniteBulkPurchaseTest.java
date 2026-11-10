package com.codex.blacklight.power;

import static org.junit.jupiter.api.Assertions.*;
import com.codex.blacklight.state.InfiniteProgress;
import org.junit.jupiter.api.Test;

class InfiniteBulkPurchaseTest {
    @Test void bulkBuysOnlyWhatTheBalancePays(){
        assertEquals(10,InfiniteEvolutionSystem.bulkCount(10,5000,200,0));
        assertEquals(3,InfiniteEvolutionSystem.bulkCount(10,700,200,0));
        assertEquals(0,InfiniteEvolutionSystem.bulkCount(10,100,200,0));
        assertEquals(InfiniteEvolutionSystem.MAX_BULK,InfiniteEvolutionSystem.bulkCount(1_000_000,Integer.MAX_VALUE,1,0));
        assertEquals(1,InfiniteEvolutionSystem.bulkCount(10,Integer.MAX_VALUE,1,Long.MAX_VALUE-1));
    }
    @Test void progressAddsSeveralRanksAndSaturates(){
        assertEquals(25,InfiniteProgress.EMPTY.buy(EvolutionAttribute.STRENGTH,25).rank(EvolutionAttribute.STRENGTH));
        var nearMax=InfiniteProgress.EMPTY.buy(EvolutionAttribute.STRENGTH,Long.MAX_VALUE-2);
        assertEquals(Long.MAX_VALUE,nearMax.buy(EvolutionAttribute.STRENGTH,10).rank(EvolutionAttribute.STRENGTH));
    }
}
