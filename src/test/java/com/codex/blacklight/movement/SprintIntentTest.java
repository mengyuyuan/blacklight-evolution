package com.codex.blacklight.movement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SprintIntentTest {
    @Test void releasingForwardStopsEvenWithStaleEcho(){
        assertFalse(SprintIntent.resolve(false,false,true,true,true));
    }
    @Test void plainForwardWalks(){
        assertFalse(SprintIntent.resolve(false,true,false,false,false));
        // A stale echo while W is already held is not a double tap.
        assertFalse(SprintIntent.resolve(false,true,true,false,true));
    }
    @Test void sprintKeyAndDoubleTapRun(){
        assertTrue(SprintIntent.resolve(true,true,true,false,false));
        assertTrue(SprintIntent.resolve(false,true,false,false,true));
    }
    @Test void heldForwardKeepsRunningAfterSprintKeyRelease(){
        assertTrue(SprintIntent.resolve(false,true,true,true,false));
    }
}
