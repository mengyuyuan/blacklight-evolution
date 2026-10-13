package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponMotionProfileTest {
    @Test void footworkDoesNotSnapBackOrDoubleRushDistance() {
        for(var move:WeaponMove.values()) {
            var p=WeaponMotionProfile.forMove(move);if(p==null)continue;
            float previous=0;
            for(int i=0;i<=move.duration*3;i++) {
                float distance=p.distanceAt(move,i/60F);
                assertTrue(distance>=previous&&distance<=p.step()+.00001);previous=distance;
            }
            assertEquals(p.step(),previous,.00001);
        }
    }
}
