package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatPoseTransitionTest {
    private static BlacklightPose pose(float x) {
        var pose=new BlacklightPose();pose.orElseEmpty("Arm_R").translation().x=x;return pose;
    }
    private static float x(BlacklightPose pose) {return pose.get("Arm_R").translation().x;}
    @Test void comboStartsAtPreviousAttackAndExitDoesNotSnapToIdle() {
        var blend=new CombatPoseTransition();
        blend.commit(null,0,pose(0),pose(0),2);
        blend.commit("first",1,pose(0),pose(10),2);
        blend.commit("first",2,pose(0),pose(10),2);
        blend.commit("second",3,pose(0),pose(-10),2);
        assertEquals(10,x(blend.sample(pose(-10),0)),.0001);
        blend.commit("second",4,pose(0),pose(-10),2);
        blend.commit(null,5,pose(0),pose(0),2.4);
        assertEquals(-10,x(blend.sample(pose(0),0)),.0001);
        blend.commit(null,6,pose(0),pose(0),2.4);
        blend.commit(null,7,pose(0),pose(0),2.4);
        assertEquals(0,x(blend.sample(pose(0),1)),.0001);
    }
    @Test void interruptedBlendUsesItsBlendedEndpoint() {
        var blend=new CombatPoseTransition();
        blend.commit(null,0,pose(0),pose(0),2);
        blend.commit("first",1,pose(0),pose(10),2);
        blend.commit("second",2,pose(0),pose(-10),2);
        assertEquals(5,x(blend.sample(pose(-10),0)),.0001);
    }
    @Test void renderOrderAndRenderMutationsCannotChangeTransition() {
        var blend=new CombatPoseTransition();
        blend.commit(null,0,pose(0),pose(0),2);
        blend.commit("attack",1,pose(0),pose(10),2);
        float expected=x(blend.sample(pose(10),.25F));
        blend.sample(pose(10),.9F).get("Arm_R").translation().x=999;
        assertEquals(expected,x(blend.sample(pose(10),.25F)),.0001);
        blend.commit("ignored",1,pose(999),pose(999),2);
        assertEquals(expected,x(blend.sample(pose(10),.25F)),.0001);
        blend.commit("next",2,pose(0),pose(-10),2);
        assertEquals(5,x(blend.sample(pose(-10),0)),.0001);
    }
    @Test void frameRateDoesNotChangeNextTickPose() {
        var low=new CombatPoseTransition();var high=new CombatPoseTransition();
        for(var blend:new CombatPoseTransition[]{low,high}) {
            blend.commit(null,0,pose(0),pose(0),2);
            blend.commit("attack",1,pose(0),pose(10),2);
        }
        low.sample(pose(10),.5F);
        for(int i=0;i<100;i++)high.sample(pose(10),i/100F);
        low.commit("attack",2,pose(0),pose(12),2);
        high.commit("attack",2,pose(0),pose(12),2);
        assertEquals(x(low.sample(pose(11),.5F)),x(high.sample(pose(11),.5F)),.0001);
    }
}
