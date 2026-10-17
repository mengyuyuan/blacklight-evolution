package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class CarryPoseLayerTest {
    @Test void movingFeetAndWaistStayConnectedWhileBothPalmsFollowCargoTogether() {
        var skeleton = BlacklightSkeleton.get();
        for (var hands : new String[]{"one", "two"}) for (var phase : new String[]{"pickup", "hold", "charge", "release"}) {
            var action = MotionLibrary.pose("block_throw_" + hands + "_" + phase, .2F, false);
            for (float time : new float[]{.05F, .2F, .4F}) {
                var stride = MotionLibrary.pose("basic_run", time, true);
                var mixed = CarryPoseLayer.apply(action, stride, 1);
                var anchor=skeleton.getBoundTransformFor(stride,skeleton.torso).getTranslation(new Vector3f());
                assertTrue(anchor.equals(skeleton.getBoundTransformFor(mixed,skeleton.torso).getTranslation(new Vector3f()),.0001F),hands+phase+" waist anchor");
                var shift=new Vector3f(anchor).sub(skeleton.getBoundTransformFor(action,skeleton.torso).getTranslation(new Vector3f()));
                for (String name : new String[]{"Root", "Thigh_R", "Thigh_L", "Knee_R", "Knee_L", "Leg_R", "Leg_L"}) {
                    var bone = skeleton.searchJointByName(name);
                    assertTrue(skeleton.getBoundTransformFor(stride, bone).equals(skeleton.getBoundTransformFor(mixed, bone), .0001F), name);
                }
                for (String name : new String[]{"Head", "Hand_R", "Hand_L", "Tool_R", "Tool_L"}) {
                    var bone = skeleton.searchJointByName(name);
                    var expected=skeleton.getBoundTransformFor(action,bone);
                    expected.setTranslation(expected.getTranslation(new Vector3f()).add(shift));
                    assertTrue(expected.equals(skeleton.getBoundTransformFor(mixed, bone), .0001F), hands + phase + name);
                }
            }
        }
    }

    @Test void partialLayerMovesGripWithWaistWithoutStretchingOrMutatingTheSource() {
        var skeleton = BlacklightSkeleton.get();
        var action = MotionLibrary.pose("block_throw_two_hold", .2F, true);
        var before = action.copy();
        var stride = MotionLibrary.pose("basic_walk", .3F, true);
        for (float weight : new float[]{0, .25F, .5F, 1}) {
            var mixed = CarryPoseLayer.apply(action, stride, weight);
            var expectedAnchor=skeleton.getBoundTransformFor(BlacklightPose.interpolatePose(action,stride,weight),skeleton.torso).getTranslation(new Vector3f());
            var actualAnchor=skeleton.getBoundTransformFor(mixed,skeleton.torso).getTranslation(new Vector3f());
            assertTrue(expectedAnchor.equals(actualAnchor,.0001F));
            var shift=new Vector3f(actualAnchor).sub(skeleton.getBoundTransformFor(action,skeleton.torso).getTranslation(new Vector3f()));
            for (String name : new String[]{"Hand_R", "Hand_L"}) {
                var bone = skeleton.searchJointByName(name);
                var expected=skeleton.getBoundTransformFor(before,bone);
                expected.setTranslation(expected.getTranslation(new Vector3f()).add(shift));
                assertTrue(expected.equals(skeleton.getBoundTransformFor(mixed, bone), .0001F));
            }
            var oldCenter=com.codex.blacklight.combat.BlockThrowMotion.localCenter(before,true);
            var newCenter=com.codex.blacklight.combat.BlockThrowMotion.localCenter(mixed,true);
            assertTrue(new Vector3f(oldCenter).add(shift).equals(newCenter,.0001F));
        }
        for (var bone : skeleton.bones()) assertTrue(skeleton.getBoundTransformFor(before, bone).equals(skeleton.getBoundTransformFor(action, bone), .00001F));
    }
}
