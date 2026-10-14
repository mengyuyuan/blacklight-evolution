package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarryPoseLayerTest {
    @Test void movingFeetKeepStrideWhileBothPalmsKeepTheCargoGrip() {
        var skeleton = BlacklightSkeleton.get();
        for (var hands : new String[]{"one", "two"}) for (var phase : new String[]{"pickup", "hold", "charge", "release"}) {
            var action = MotionLibrary.pose("block_throw_" + hands + "_" + phase, .2F, false);
            for (float time : new float[]{.05F, .2F, .4F}) {
                var stride = MotionLibrary.pose("basic_run", time, true);
                var mixed = CarryPoseLayer.apply(action, stride, 1);
                for (String name : new String[]{"Root", "Thigh_R", "Thigh_L", "Knee_R", "Knee_L", "Leg_R", "Leg_L"}) {
                    var bone = skeleton.searchJointByName(name);
                    assertTrue(skeleton.getBoundTransformFor(stride, bone).equals(skeleton.getBoundTransformFor(mixed, bone), .0001F), name);
                }
                for (String name : new String[]{"Head", "Hand_R", "Hand_L", "Tool_R", "Tool_L"}) {
                    var bone = skeleton.searchJointByName(name);
                    assertTrue(skeleton.getBoundTransformFor(action, bone).equals(skeleton.getBoundTransformFor(mixed, bone), .0001F), hands + phase + name);
                }
            }
        }
    }

    @Test void partialLayerDoesNotMoveTheGripOrMutateTheSource() {
        var skeleton = BlacklightSkeleton.get();
        var action = MotionLibrary.pose("block_throw_two_hold", .2F, true);
        var before = action.copy();
        var stride = MotionLibrary.pose("basic_walk", .3F, true);
        for (float weight : new float[]{0, .25F, .5F, 1}) {
            var mixed = CarryPoseLayer.apply(action, stride, weight);
            for (String name : new String[]{"Hand_R", "Hand_L"}) {
                var bone = skeleton.searchJointByName(name);
                assertTrue(skeleton.getBoundTransformFor(before, bone).equals(skeleton.getBoundTransformFor(mixed, bone), .0001F));
            }
        }
        for (var bone : skeleton.bones()) assertTrue(skeleton.getBoundTransformFor(before, bone).equals(skeleton.getBoundTransformFor(action, bone), .00001F));
    }
}
