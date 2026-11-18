package com.codex.blacklight.military.aircraft;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Deferred source-branch regressions; no runtime acceptance is implied. */
class HelicopterTacticBankTest {
    private static <T> HelicopterTacticBank.Branch<T> branch(T value,float weight){
        return new HelicopterTacticBank.Branch<>(value,weight,()->true);
    }
    @Test void sourceWeightsDoNotTurnTenThousandIntoAnUnconditionalPriority(){
        var branches=List.of(branch("circle",50),branch("strafe",100),branch("simple",30),branch("hover",10000));
        assertEquals("circle",HelicopterTacticBank.choose(branches,()->0));
        assertEquals("circle",HelicopterTacticBank.choose(branches,()->50d/10180));
        assertEquals("strafe",HelicopterTacticBank.choose(branches,()->100d/10180));
        assertEquals("simple",HelicopterTacticBank.choose(branches,()->170d/10180));
        assertEquals("hover",HelicopterTacticBank.choose(branches,()->.99));
    }
    @Test void firstEligibleNonpositiveWeightWinsBeforeRandomAndLaterConditions(){
        var visits=new AtomicInteger();
        var branches=List.of(branch("positive",100),branch("immediate",0),new HelicopterTacticBank.Branch<>("later",10,()->{visits.incrementAndGet();return true;}));
        assertEquals("immediate",HelicopterTacticBank.choose(branches,()->{throw new AssertionError("no random draw");}));
        assertEquals(0,visits.get());
    }
    @Test void theSecondPassMustReevaluateAndMayFailInsteadOfChoosingAStaleCandidate(){
        var visits=new AtomicInteger();
        var changing=new HelicopterTacticBank.Branch<>("changed",100,()->visits.incrementAndGet()==1);
        assertNull(HelicopterTacticBank.choose(List.of(changing),()->.5));
        assertEquals(2,visits.get());
    }
    @Test void sourceTokenRequestCanAcquireEvenWhenTheWeightedBankChoosesHover(){
        var actor=new UUID(3,4);var other=new UUID(5,6);var lease=new HelicopterAttackLease();var requests=new AtomicInteger();
        var strafe=new HelicopterTacticBank.Branch<>("strafe",100,()->{requests.incrementAndGet();return lease.request(actor,id->true);});
        assertEquals("hover",HelicopterTacticBank.choose(List.of(strafe,branch("hover",10000)),()->.9));
        assertEquals(2,requests.get());assertEquals(actor,lease.owner());assertEquals(.5F,lease.remainingSeconds());
        assertFalse(lease.available(other,id->true),"unselected branch still requested the original short lease");
    }
    @Test void emptyAndRejectedBanksDoNotConsumeRandom(){
        assertNull(HelicopterTacticBank.choose(List.<HelicopterTacticBank.Branch<String>>of(),()->{throw new AssertionError();}));
        assertNull(HelicopterTacticBank.choose(List.of(new HelicopterTacticBank.Branch<>("no",1,()->false)),()->{throw new AssertionError();}));
    }
}
