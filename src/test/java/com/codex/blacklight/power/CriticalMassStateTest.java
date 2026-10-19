package com.codex.blacklight.power;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CriticalMassStateTest {
    @Test void reserveAbsorbsBeforeHealthAndOnlyOverflowReachesHealth() {
        var state=new CriticalMassState(1,true,100);
        var small=state.absorb(10,100);
        assertEquals(0,small.remaining());assertEquals(10,small.absorbed());
        assertEquals(.6F,small.state().reserve(),.0001F);
        assertEquals(100,small.state().lastAdrenalineDevastatorAt());
        var overflow=state.absorb(40,100);
        assertEquals(25,overflow.absorbed());assertEquals(15F,overflow.remaining());
        assertEquals(0,overflow.state().reserve());
        assertEquals(40,overflow.state().absorb(40,100).remaining());
        var zero=state.absorb(0,100);
        assertSame(state,zero.state());assertEquals(0,zero.absorbed());
    }
    @Test void consumeOverflowAndDamageShareCapacityAfterHealthGrowth() {
        var state=CriticalMassState.EMPTY.addOverheal(10,100);
        assertEquals(.22F,state.reserve(),.0001F);
        assertTrue(state.ready());
        state=state.addOverheal(100,200);
        assertTrue(state.ready());
        assertEquals(.5F,state.damaged(25,200).reserve(),.0001F);
        assertEquals(0,state.damaged(500,200).reserve());
        assertEquals(0,state.spend().reserve());
    }
    @Test void malformedOrNegativeAmountsCannotGrantReserve() {
        var state=new CriticalMassState(.5F,false);
        assertSame(state,state.addOverheal(-1,100));
        assertSame(state,state.addOverheal(Float.NaN,100));
        assertSame(state,state.addOverheal(10,Float.POSITIVE_INFINITY));
        assertSame(state,state.damaged(-1,100));
        assertEquals(0,new CriticalMassState(Float.NaN,true).reserve());
        assertEquals(4,new CriticalMassState(20,true).reserve());
    }
    @Test void boostsKeepRemainingChargesAfterEachRelease() {
        var state=CriticalMassState.EMPTY.addOverheal(1000,100,4);
        assertEquals(4,state.reserve());
        assertEquals(3,state.spend().reserve());
        assertTrue(state.spend().ready());
        assertEquals(0,state.spend().spend().spend().spend().reserve());
        assertFalse(state.spend().spend().spend().spend().ready());
        assertEquals(2,CriticalMassState.EMPTY.addOverheal(1000,100,2).reserve());
    }
    @Test void anyPositiveReserveReleasesAndOnlyShortfallCostsHealth() {
        var tiny=new CriticalMassState(.001F,false,500);
        assertTrue(tiny.ready());
        var spent=tiny.spend(100);
        assertEquals(0,spent.state().reserve());
        assertEquals(24.975F,spent.healthLoss(),.0001F);
        assertEquals(500,spent.state().lastAdrenalineDevastatorAt());
        assertFalse(spent.state().adrenalineArmed());
        var exact=new CriticalMassState(1,true).spend(100);
        assertEquals(0,exact.state().reserve());assertEquals(0,exact.healthLoss());
        var excess=new CriticalMassState(1.001F,true).spend(100);
        assertEquals(.001F,excess.state().reserve(),.0001F);assertEquals(0,excess.healthLoss());
        var empty=CriticalMassState.EMPTY.spend(100);
        assertSame(CriticalMassState.EMPTY,empty.state());assertEquals(0,empty.healthLoss());
        assertFalse(empty.state().ready());
    }
    @Test void exactDamageDepletionDoesNotChargeRedHealth() {
        var state=new CriticalMassState(.1F,true,800);
        var exact=state.absorb(2.5F,100);
        assertEquals(2.5F,exact.absorbed());assertEquals(0,exact.remaining());
        assertEquals(0,exact.state().reserve());
        assertEquals(1,exact.state().absorb(1,100).remaining());
        var partial=state.absorb(2.49F,100);
        assertTrue(partial.state().ready());assertEquals(0,partial.remaining());
        assertEquals(800,exact.state().lastAdrenalineDevastatorAt());
        var grown=new CriticalMassState(.1F,true).spend(200);
        assertEquals(45,grown.healthLoss(),.0001F);
    }
    @Test void savedReserveDoesNotRearmAnAlreadySpentLastChance() {
        var state=new CriticalMassState(.7F,false,500);
        var encoded=CriticalMassState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow(false,message->{throw new IllegalStateException(message);});
        var decoded=CriticalMassState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow(false,message->{throw new IllegalStateException(message);});
        assertEquals(state,decoded);
        assertFalse(decoded.spend().adrenalineArmed());
        assertTrue(decoded.armed(true).adrenalineArmed());
        assertEquals(CriticalMassState.EMPTY,CriticalMassState.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{}")).getOrThrow(false,message->{throw new IllegalStateException(message);}));
    }
    @Test void adrenalineIntervalPersistsAndDoesNotRestrictOrdinaryReserve() {
        var state=new CriticalMassState(2,false).adrenalineSpent(1000);
        assertFalse(state.adrenalineReleaseAllowed(999));
        assertFalse(state.adrenalineReleaseAllowed(1449));
        assertEquals(1,state.adrenalineWaitTicks(1449));
        assertTrue(state.adrenalineReleaseAllowed(1450));
        assertEquals(0,state.adrenalineWaitTicks(1450));
        assertTrue(state.ready());
        assertEquals(1,state.spend().reserve());
        assertEquals(1000,state.spend().lastAdrenalineDevastatorAt());
        assertEquals(1000,state.armed(true).lastAdrenalineDevastatorAt());
        assertEquals(1000,state.damaged(1,100).lastAdrenalineDevastatorAt());
        assertEquals(1000,state.addOverheal(1,100,4).lastAdrenalineDevastatorAt());
        assertTrue(CriticalMassState.EMPTY.adrenalineReleaseAllowed(0));
    }
    @Test void adrenalineReleaseIsSingleUseAndExpiryDoesNotExtendGrace() {
        var state=new AdrenalineState(200,136,true);
        assertTrue(state.undamageable(135));
        assertFalse(state.undamageable(136));
        assertTrue(state.ready(199));
        var spent=state.spend();
        assertFalse(spent.ready(199));
        assertTrue(spent.active(199));
        assertEquals(200,spent.until());
        assertEquals(136,spent.undamageableUntil());
        assertFalse(spent.active(200));
        assertFalse(state.ready(200));
        assertFalse(new AdrenalineState(-50,true).chargeAvailable());
    }
}
