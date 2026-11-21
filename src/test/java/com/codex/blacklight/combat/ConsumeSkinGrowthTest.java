package com.codex.blacklight.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeSkinGrowthTest {
    // Golden samples from running prototypeenginef.dll's ConsumeEffect2 update on these bones.
    private static final int[] PARENTS = {-1, 0, 1, 1};
    private static final float[] LENGTHS = {.5F, 2F, .4F, .25F};
    private static final ConsumeSkinGrowth.Skeleton RIG = new ConsumeSkinGrowth.Skeleton(PARENTS, LENGTHS);

    private void compare(String clip, int seed, int frame, float[] proximal, float[] distal) {
        var actual = RIG.sample(ConsumeReceiverEffect.forClip(clip), frame/60F, seed);
        for (int i = 0; i < PARENTS.length; i++) {
            assertEquals(proximal[i], actual.proximal(i), 1e-5F);
            assertEquals(distal[i], actual.distal(i), 1e-5F);
            assertEquals(.25F*proximal[i]+.75F*distal[i], actual.coverage(i, .75F), 1e-5F);
        }
    }

    @Test void forwardOpensTheNeighborGateThenSplitsIntoBothChildren() {
        compare("normal", 1, 17, new float[]{0,.56767F,0,0}, new float[]{0,.56767F,0,0});
        compare("normal", 1, 18, new float[]{0,.601F,.05337F,.05337F}, new float[]{.02003F,.601F,.03333F,.03333F});
        compare("normal", 1, 24, new float[]{.2F,.801F,.39690F,.39690F}, new float[]{.36357F,.801F,.23333F,.23333F});
        compare("normal", 1, 60, new float[]{1,1,1,1}, new float[]{1,1,1,1});
    }

    @Test void backwardClearsTheSeedFirstAndTransmitsThroughALongParentBone() {
        compare("muscle_heart", 2, 0, new float[]{1,1,.001F,1}, new float[]{1,1,.001F,1});
        compare("muscle_heart", 2, 1, new float[]{1,.99665F,0,1}, new float[]{.99665F,.99330F,0,1});
        compare("muscle_heart", 2, 30, new float[]{.80667F,.68904F,0,.86185F}, new float[]{.68893F,.59667F,0,.9F});
        compare("muscle_heart", 2, 120, new float[]{.20667F,0,0,0}, new float[]{0,0,0,.3F});
    }

    @Test void drawingTheSameAgeAgainCannotAdvanceTheEffect() {
        var cue = ConsumeReceiverEffect.forClip("normal");
        var first = RIG.sample(cue, .37F, 1);
        RIG.sample(cue, .71F, 1);
        var repeated = ConsumeSkinGrowth.sample(cue, .37F, PARENTS, LENGTHS, 1);
        for (int i = 0; i < PARENTS.length; i++) {
            assertEquals(first.bone(i), repeated.bone(i));
            assertEquals(0, RIG.sample(ConsumeReceiverEffect.forClip("muscle_heart"), -.01F, 2).bone(i));
        }
    }

    @Test void cachedPlaybackMatchesIndependentSamplingAndKeepsEarlierSnapshotsAlive() {
        var cue = ConsumeReceiverEffect.forClip("normal");
        var live = RIG.playback(cue, 1);
        var original = live.sample(.14F);
        float initial = original.bone(1);
        for (float age : new float[]{.14F,.149F,.171F,.76F,1.53F,.26F,.13F,.879F,-.2F,.6F}) {
            var a = RIG.sample(cue, age, 1);
            var b = live.sample(age);
            assertSame(b, live.sample(age));
            for (int i = 0; i < PARENTS.length; i++) {
                assertEquals(a.proximal(i), b.proximal(i));
                assertEquals(a.distal(i), b.distal(i));
            }
        }
        assertEquals(initial, original.bone(1));
    }

    @Test void originalSkinCoefficientsAreIndependentReciprocalDistances() {
        var w = RIG.sample(ConsumeReceiverEffect.forClip("normal"), 18/60F, 2);
        float p=w.proximal(1), d=w.distal(1);
        assertNotEquals(p, d);
        assertEquals(p*(127F/255F)+d*(63F/255F), w.influence(1,4,2), .000001);
        assertEquals(p*(63F/255F)+d*(127F/255F), w.influence(1,2,4), .000001);
        assertEquals(p+d, w.influence(1,0,0), .000001);
        assertEquals(p+d, w.influence(1,.25F,.5F), .000001);
    }
}
