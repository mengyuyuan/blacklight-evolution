package com.codex.blacklight.resource;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMotionIsolationTest {
    @Test void independentClipsAreNotPublishedToEpicFightAnimationDiscovery() {
        // Epic Fight interprets every JSON below this directory as one of its
        // animation definitions, including constructor metadata we do not use.
        assertNull(getClass().getResource("/assets/blacklight/animmodels/animations"));
        assertNotNull(getClass().getResource(
                "/assets/blacklight/native_animations/biped/combat/claw_auto1.json"));
    }
}
