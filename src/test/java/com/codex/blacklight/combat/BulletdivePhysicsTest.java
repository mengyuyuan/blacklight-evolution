package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulletdivePhysicsTest {
    @Test void continuedFallNeverFlattensAtOldTerminalSpeed() {
        assertTrue(BulletdivePhysics.downSpeed(30)>5);
        assertTrue(BulletdivePhysics.downSpeed(600)>BulletdivePhysics.downSpeed(200));
        assertTrue(BulletdivePhysics.downSpeed(601)-BulletdivePhysics.downSpeed(600)
                >BulletdivePhysics.downSpeed(201)-BulletdivePhysics.downSpeed(200),"Acceleration itself must keep growing");
    }
    @Test void extremeDropDoesNotRequireMinutesOfConstantAcceleration() {
        double distance=0,previous=0,previousGain=0;int ticks=0;
        while(distance<9_999_999&&ticks<1600){
            double speed=BulletdivePhysics.downSpeed(ticks++);
            assertTrue(Double.isFinite(speed));assertTrue(speed>previous);
            if(ticks>2)assertTrue(speed-previous>previousGain);
            previousGain=speed-previous;previous=speed;distance+=speed;
        }
        assertTrue(ticks<1200,"Ten-million-block dive should take under a simulated minute");
        assertTrue(previous>10_000);
        assertTrue(BulletdivePhysics.downSpeed(ticks+200)>previous);
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
    @Test void highDropsHaveEnergyToKillLargeHealthPoolsWithoutNumericOverflow() {
        assertEquals(44F,BulletdivePhysics.impactDamage(22,30),.001F);
        assertEquals(66F,BulletdivePhysics.impactDamage(22,60),.001F);
        assertEquals(44022F,BulletdivePhysics.impactDamage(22,60000),.01F);
        assertTrue(BulletdivePhysics.impactDamage(22,9_999_999)>7_000_000);
        assertTrue(Float.isFinite(BulletdivePhysics.impactDamage(22,Double.MAX_VALUE)));
        assertEquals(22F,BulletdivePhysics.impactDamage(22,Double.NaN));
        assertEquals(0F,BulletdivePhysics.impactDamage(Float.NaN,100));
    }
}
