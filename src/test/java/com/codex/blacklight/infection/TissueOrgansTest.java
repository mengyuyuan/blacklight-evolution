package com.codex.blacklight.infection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TissueOrgansTest {
    @Test void organsBecomeLargerInThreeDimensionsAndRetainTheirIdentity() {
        for(long seed = 0; seed < 100; seed++) {
            assertTrue(TissueOrgans.count(seed) >= 4 && TissueOrgans.count(seed) <= 5);
            for(int slot = 0; slot < TissueOrgans.count(seed); slot++) {
                float length = -1, radius = -1;
                for(int age = 0; age <= TissueGrowth.ANCIENT_TICKS; age += 100) {
                    var organ = TissueOrgans.organ(seed, age, slot);
                    assertEquals(organ, TissueOrgans.organ(seed, age, slot));
                    assertTrue(organ.length() >= length && organ.radius() >= radius);
                    length = organ.length(); radius = organ.radius();
                }
                assertTrue(length > .45F && radius >= .16F, "mature organs must have volume, not skin thickness");
            }
        }
    }
    @Test void mutationIsLocalAndAsymmetricAndReachRequiresMatureTendril() {
        long seed = 171;
        int arm = TissueOrgans.dominantArm(seed);
        assertFalse(TissueOrgans.mutation(seed, 0, arm).active());
        assertTrue(TissueOrgans.mutation(seed, 12000, arm).y() > 1.5F);
        assertFalse(TissueOrgans.mutation(seed, 12000, arm == 2 ? 3 : 2).active());
        assertFalse(TissueOrgans.mutation(seed, 12000, 4).active(), "do not push feet below terrain");
        assertEquals(0, TissueOrgans.reach(seed, 0, 1, 1));
        assertTrue(TissueOrgans.reach(seed, 12000, 1, 1) > .5F);
        assertTrue(TissueOrgans.reach(seed, 12000, 100, 100) < 3);
    }
    @Test void strikeHasWindupContactAndRetractionWithoutFrameRandomness() {
        assertEquals(0, TissueOrgans.extension(0));
        assertEquals(0, TissueOrgans.extension(TissueOrgans.WINDUP));
        assertEquals(1, TissueOrgans.extension(TissueOrgans.HIT));
        assertEquals(0, TissueOrgans.extension(TissueOrgans.DURATION));
        for(float t = 0; t < 40; t += .1F) assertTrue(TissueOrgans.extension(t) >= 0 && TissueOrgans.extension(t) <= 1);
    }
}
