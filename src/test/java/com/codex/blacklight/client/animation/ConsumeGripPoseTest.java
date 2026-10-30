package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightSkeleton;
import com.codex.blacklight.animation.MotionLibrary;
import com.codex.blacklight.combat.ConsumePlacementTrack;
import com.codex.blacklight.power.PowerForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeGripPoseTest {
    @Test void footSweepShoulderSocketsRemainOnTheirOwnSideOfTheChest() {
        var skeleton=BlacklightSkeleton.get();
        int chest=skeleton.searchJointByName("Chest").getId();
        float duration=MotionLibrary.duration("consume_claws_actor");
        for(int frame=0;frame<=260;frame++) {
            var matrices=skeleton.getPoseMatrices(MotionLibrary.pose("consume_claws_actor",duration*frame/260F,false));
            var chestInverse=new org.joml.Matrix4f(matrices[chest]).invert();
            for(String side:new String[]{"R","L"}) {
                var arm=skeleton.searchJointByName("Arm_"+side);
                var socket=new org.joml.Matrix4f(chestInverse).mul(matrices[arm.getId()]).getTranslation(new org.joml.Vector3f());
                assertTrue(socket.x*(side.equals("R")?1:-1)>.05F,"frame "+frame+" crossed "+side+" shoulder: "+socket);
                assertTrue(Math.abs(socket.z)<.35F,"frame "+frame+" displaced shoulder behind torso: "+socket);
            }
        }
    }
    @Test void footSweepPreservesEveryAuthoredActorFrameWithoutLookingForAHeadContact() {
        assertFalse(ConsumePlacementTrack.headGrip(PowerForm.CLAWS));
        var skeleton=BlacklightSkeleton.get();
        float duration=MotionLibrary.duration("consume_claws_actor");
        for(int frame=0;frame<=120;frame++) {
            var pose=MotionLibrary.pose("consume_claws_actor",duration*frame/120F,false);
            var expected=pose.copy();
            // A scene without palm fitting must not require a victim/world query.
            var visual=new com.codex.blacklight.combat.ConsumeVisual(1,0,88,PowerForm.CLAWS.ordinal(),com.codex.blacklight.combat.ConsumeVisual.CONSUME);
            ConsumeGripPose.applyForScene(null,null,skeleton,pose,visual,.5F);
            assertEquals(expected.getJointTransformData().keySet(),pose.getJointTransformData().keySet());
            for(var entry:expected.getJointTransformData().entrySet()) {
                var actual=pose.get(entry.getKey());var before=entry.getValue();
                String label="frame "+frame+" joint "+entry.getKey();
                assertEquals(before.translation(),actual.translation(),label);
                assertEquals(before.rotation(),actual.rotation(),label);
                assertEquals(before.scale(),actual.scale(),label);
            }
        }
    }
}
