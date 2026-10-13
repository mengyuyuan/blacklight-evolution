package com.codex.blacklight.client.renderer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.codex.blacklight.combat.DevastatorEffectTimeline.*;
class BulletImpactMistTest {
    @Test void plumeStartsAfterItsDelayAndFadesWithoutFrameRandomness(){
        assertEquals(0,impactMistOpacity(0,.1));
        assertTrue(impactMistOpacity(.3,.1)>.8);
        assertTrue(impactMistOpacity(2,.1)<impactMistOpacity(1,.1));
        assertEquals(0,impactMistOpacity(3.2,.1));
        assertEquals(0,impactMistOpacity(Double.NaN,.1));
        assertEquals(0,impactMistOpacity(Double.POSITIVE_INFINITY,.1));
    }
    @Test void redClustersGrowWithHeightAndOnlyStrictlyOverFiftyAddsMist(){
        double radius=0,height=0;
        for(int h=0;h<=50;h++){
            var p=impact(h);assertFalse(p.mist());assertEquals(0,p.mistGain());
            assertTrue(p.clusterRadius()>radius);assertTrue(p.clusterHeight()>height);
            radius=p.clusterRadius();height=p.clusterHeight();
        }
        assertTrue(impact(Math.nextUp(50D)).mist());assertTrue(impact(80).mistGain()>impact(51).mistGain());
        assertFalse(impact(Double.NaN).mist());assertFalse(impact(Double.POSITIVE_INFINITY).mist());
        assertEquals(0,impactClusterOpacity(-1));assertTrue(impactClusterOpacity(.1)>.9);
        assertEquals(0,impactClusterOpacity(.8));assertEquals(0,impactClusterOpacity(Double.NaN));
    }
}
