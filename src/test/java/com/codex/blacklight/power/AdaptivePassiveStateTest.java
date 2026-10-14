package com.codex.blacklight.power;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptivePassiveStateTest {
    @Test void oldOrEmptySaveGetsNoFreePassives() {
        assertEquals(AdaptivePassiveState.EMPTY,AdaptivePassiveState.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{}" )).getOrThrow(false, message -> { throw new AssertionError(message); }));
    }
    @Test void savedPotionFlagsAndStacksRoundTripAndRepeatedGrantDoesNotAddStacks() {
        var state=new AdaptivePassiveState(3,86,20,100);
        var encoded=AdaptivePassiveState.CODEC.encodeStart(JsonOps.INSTANCE,state).getOrThrow(false, message -> { throw new AssertionError(message); });
        assertEquals(state,AdaptivePassiveState.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow(false, message -> { throw new AssertionError(message); }));
        assertEquals(state,state.grant(1));
        assertTrue(state.permanent(2));
    }
    @Test void malformedCountsAreSanitized() {
        assertEquals(new AdaptivePassiveState(3,0,0,0),new AdaptivePassiveState(-1,-9,-2,-20));
    }
}
