package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulletdivePhysicsTest {
    @Test void continuedFallNeverFlattensAtOldTerminalSpeed() {
        assertTrue(BulletdivePhysics.downSpeed(30)>5);
        assertTrue(BulletdivePhysics.downSpeed(600)>BulletdivePhysics.downSpeed(200));
        assertEquals(.22,BulletdivePhysics.downSpeed(601)-BulletdivePhysics.downSpeed(600),1e-10);
    }
    @Test void damageContinuesGrowingBeyondOldHeightCaps() {
        assertTrue(BulletdivePhysics.damageMultiplier(1024)>BulletdivePhysics.damageMultiplier(512));
        assertTrue(BulletdivePhysics.damageMultiplier(10000)>BulletdivePhysics.damageMultiplier(1024));
        assertEquals(1,BulletdivePhysics.damageMultiplier(0));
    }
}
