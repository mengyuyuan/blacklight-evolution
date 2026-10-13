package com.codex.blacklight.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClawActionPayloadTest {
    @Test void everyValidIdRoundTripsIncludingZero() {
        for(var action:ClawActionPayload.Action.values())assertEquals(action,ClawActionPayload.Action.byId(action.id()));
    }
    @Test void invalidIdsCannotTurnIntoGameplayActions() {
        for(int id:new int[]{-1,Integer.MIN_VALUE,12,Integer.MAX_VALUE})
            assertThrows(IllegalArgumentException.class,()->ClawActionPayload.Action.byId(id));
    }
}
