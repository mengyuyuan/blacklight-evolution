package com.codex.blacklight.combat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static com.codex.blacklight.combat.ConsumeReceiverEffect.Direction.*;
import static com.codex.blacklight.combat.ConsumeReceiverEffect.Joint.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeReceiverEffectTest {
    @Test void matchesEveryOrdinaryReceiverBranch() {
        record Row(String clip, int frame, ConsumeReceiverEffect.Joint joint, ConsumeReceiverEffect.Direction direction,
                   float rate, float threshold) {}
        var expected = new Row[] {
            new Row("normal", 20, SPINE_2, FORWARD, .5F, .6F),
            new Row("normal_knee_head", 20, SPINE_2, FORWARD, .5F, .6F),
            new Row("normal_head_stomp", 20, NECK, FORWARD, .9F, .8F),
            new Row("normal_beatdown", 88, NECK, FORWARD, .4F, .2F),
            new Row("claws", 12, MOTION_ROOT, FORWARD, .5F, .75F),
            new Row("claws_head", 60, NECK, FORWARD, .15F, .2F),
            new Row("hammer", 72, NECK, FORWARD, .2F, .1F),
            new Row("hammer_smush", 60, NECK, FORWARD, .1F, .2F),
            new Row("muscle", 24, MOTION_ROOT, FORWARD, .5F, .1F),
            new Row("muscle_heart", 40, ANKLE_L, BACKWARD, .1F, .2F),
            new Row("blade", 20, MOTION_ROOT, FORWARD, .5F, .75F),
            new Row("whip", 42, MOTION_ROOT, FORWARD, .5F, .75F),
            new Row("whip_lash", 42, NECK, FORWARD, .7F, .8F)
        };
        var offsets = new HashSet<Integer>();
        for (var row : expected) {
            var cue = ConsumeReceiverEffect.forClip(row.clip);
            assertNotNull(cue, row.clip);
            assertEquals(row.frame, cue.startFrame());
            assertEquals(row.frame/30F, cue.startSeconds(), .000005F);
            assertEquals(row.joint, cue.joint());
            assertEquals(row.direction, cue.direction());
            assertEquals(row.rate, cue.spreadRate());
            assertEquals(row.threshold, cue.growthThreshold());
            assertEquals(0F, cue.age(cue.startSeconds()));
            assertTrue(cue.age(cue.startSeconds() - .001F) < 0);
            assertEquals(1F, cue.timeReverse());
            assertEquals(-1F, cue.timeEnd());
            assertFalse(cue.useTimeReveal());
            assertFalse(cue.useSecondStartPoint());
            assertTrue(offsets.add(cue.offset()), row.clip);
        }
    }

    @Test void unrelatedScenesKeepTheirOwnMaterial() {
        for (String name : new String[]{null, "", "stealth_neck", "mounted", "hunter", "unknown"})
            assertNull(ConsumeReceiverEffect.forClip(name));
    }
}
