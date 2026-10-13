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
    @Test void finiteActualDropsRemainMonotoneThroughLargePhysicalRanges() {
        double previous=1;
        for(int height=0;height<=20_000;height++){
            double damage=BulletdivePhysics.damageMultiplier(height);
            assertTrue(Double.isFinite(damage));assertTrue(damage>=previous);previous=damage;
        }
        assertTrue(BulletdivePhysics.damageMultiplier(1e12)>previous);
        assertTrue(Double.isFinite(BulletdivePhysics.damageMultiplier(Double.MAX_VALUE)));
        assertTrue(Double.isFinite(BulletdivePhysics.downSpeed(Double.MAX_VALUE)));
    }
    @Test void invalidInputsNeverInjectNanIntoPhysicsOrVisuals() {
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-10}){
            assertEquals(1,BulletdivePhysics.damageMultiplier(invalid));
            assertEquals(BulletdivePhysics.INITIAL_SPEED,BulletdivePhysics.downSpeed(invalid));
            assertEquals(0,BulletdivePhysics.visualIntensity(invalid,invalid));
        }
        assertTrue(Double.isFinite(BulletdivePhysics.visualIntensity(Double.MAX_VALUE,Double.MAX_VALUE)));
    }
}
