package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BulletdiveTerrainTest {
    @Test void actualHeightMonotonicallyRaisesBoundedImpact() {
        var prior=BulletdiveTerrain.plan(0);
        for(int height=1;height<=1000;height++) {
            var plan=BulletdiveTerrain.plan(height);
            assertTrue(plan.radius()>=prior.radius());assertTrue(plan.depth()>=prior.depth());
            assertTrue(plan.energy()>=prior.energy());assertTrue(plan.budget()>=prior.budget());
            assertTrue(plan.radius()<=12&&plan.depth()<=10&&plan.budget()<=4096);prior=plan;
        }
        assertEquals(0,BulletdiveTerrain.plan(6).budget());
        assertEquals(0,BulletdiveTerrain.plan(Double.NaN).budget());
        assertEquals(0,BulletdiveTerrain.plan(Double.POSITIVE_INFINITY).budget());
        assertEquals(0,BulletdiveTerrain.plan(-50).budget());
    }
    @Test void bowlGetsShallowerOutwardAndNeverExtendsOutsideItsCircle() {
        var plan=BulletdiveTerrain.plan(120);int prior=plan.depth();
        for(int distance=0;distance<=plan.radius()*plan.radius();distance++) {
            int depth=BulletdiveTerrain.columnDepth(plan,distance);
            assertTrue(depth<=prior&&depth>=0);prior=depth;
        }
        assertEquals(plan.depth(),BulletdiveTerrain.columnDepth(plan,0));
        assertEquals(0,BulletdiveTerrain.columnDepth(plan,plan.radius()*plan.radius()));
    }
    @Test void hardMaterialRequiresHigherEnergyWhileProtectedMaterialNeverQualifies() {
        var low=BulletdiveTerrain.plan(12);var high=BulletdiveTerrain.plan(80);
        assertTrue(BulletdiveTerrain.canBreak(low,0,0,.5F,2,false));
        assertFalse(BulletdiveTerrain.canBreak(low,0,0,5,6,false));
        assertTrue(BulletdiveTerrain.canBreak(high,0,0,5,6,false));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,-1,0,false));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,.1F,1200,false));
        assertFalse(BulletdiveTerrain.canBreak(high,0,0,.1F,1,true));
        assertTrue(BulletdiveTerrain.hardnessCost(5)>BulletdiveTerrain.hardnessCost(.5F));
    }
}
