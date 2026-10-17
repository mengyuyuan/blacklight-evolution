package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DodgeActionRoutingTest {
    @Test void synchronizedDodgeReachesActionPoseInsteadOfBeingDiscardedAsWalking(){
        assertFalse(PlayerMotionController.isLocomotionAction(MotionLibrary.resolve(PlayerAction.TACTICAL_HOP)));
        assertTrue(PlayerMotionController.isLocomotionAction("basic_walk"));
        assertTrue(PlayerMotionController.isLocomotionAction("basic_sprint"));
    }
}
