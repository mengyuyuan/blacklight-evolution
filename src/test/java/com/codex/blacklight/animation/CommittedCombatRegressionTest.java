package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommittedCombatRegressionTest {
    private static BlacklightPose pose(float x) {
        var p=new BlacklightPose();p.orElseEmpty("Arm_R").translation().x=x;return p;
    }
    private static float x(BlacklightPose p){return p.get("Arm_R").translation().x;}
    @Test void networkChangeBetweenRendersCannotReplaceCommittedAttack() {
        var interval=new CommittedPoseInterval();var blend=new CombatPoseTransition();
        float[] network={10};
        interval.commit(1,t->pose(network[0]+(float)t));
        blend.commit("attack",1,pose(0),interval.sample(1),1);
        float expected=x(blend.sample(interval.sample(.8F),.8F));
        network[0]=-50; // A new attachment arrives between two render passes.
        assertEquals(expected,x(blend.sample(interval.sample(.8F),.8F)),.00001);
        interval.commit(2,t->pose(network[0]+(float)t));
        blend.commit("next",2,pose(0),interval.sample(1),1);
        assertEquals(11,x(blend.sample(interval.sample(0),0)),.00001);
    }
    @Test void sourceSubframesAreKeptAndReturnedPosesCannotCorruptOtherPasses() {
        var interval=new CommittedPoseInterval();
        interval.commit(1,t->pose((float)(t*t)));
        assertEquals(1F/9,x(interval.sample(1F/3)),.00001);
        interval.sample(1).get("Arm_R").translation().x=1000;
        interval.commit(1,t->pose(999));
        assertEquals(1,x(interval.sample(1)),.00001);
    }
    @Test void recoveryUsesDecodedIdleOverlapAndLeavesContactFramesUntouched() {
        assertEquals(0,SourceClawRecovery.weight("claw_auto1",.3F));
        assertEquals(.5F,SourceClawRecovery.weight("claw_auto1",1F/30+.6F+.25F),.00001);
        assertEquals(1,SourceClawRecovery.weight("claw_auto1_charged",.96F),.00001);
        assertEquals(.5F,SourceClawRecovery.weight("claw_auto2",.8F+7F/60),.00001);
        assertEquals(0,SourceClawRecovery.weight("claw_auto1_charge",1));
        for(String key:new String[]{"claw_auto1","claw_auto2","claw_auto3"}) {
            var clip=MotionLibrary.clip(key);
            var raw=MotionLibrary.pose(key,0,false);
            assertSame(raw,SourceClawRecovery.apply(key,0,raw));
            var recovered=SourceClawRecovery.apply(key,MotionLibrary.duration(key),
                    MotionLibrary.pose(key,MotionLibrary.duration(key),false));
            assertTrue(recovered.hasTransform("Arm_R"));
            assertTrue(Float.isFinite(recovered.get("Arm_R").rotation().w));
        }
    }
    @Test void endingAttackCannotSnapBodyBackAndWrapUsesShortestArc() {
        var facing=new CombatFacingTransition();
        facing.commit(null,0,179,179,2);
        facing.commit("attack",1,179,-179,1);
        assertEquals(179,facing.sample(0),.0001);
        assertEquals(181,facing.sample(1),.0001);
        facing.commit(null,2,20,20,2.4);
        assertEquals(181,facing.sample(0),.0001);
        float expected=facing.sample(.2F);
        facing.sample(.9F);
        assertEquals(expected,facing.sample(.2F),.0001);
        facing.commit(null,3,20,20,2.4);
        facing.commit(null,4,20,20,2.4);
        assertEquals(20,facing.sample(1),.0001);
    }
}
