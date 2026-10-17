package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class WingTransformationPoseTest {
    @Test void emergenceNeverReplacesGaitOrPullsShoulderJointsApart() {
        for(boolean deployed:new boolean[]{false,true})for(float time=0;time<40;time+=.25F) {
            var pose=FlightAnimationClips.sample("hover",time/20);
            var original=pose.copy();
            WingTransformationPose.apply(pose,deployed,time);
            for(String joint:new String[]{"Root","Head","Thigh_R","Thigh_L","Leg_R","Leg_L"})
                assertTrue(Math.abs(original.get(joint).rotation().dot(pose.get(joint).rotation()))>.99999F,joint);
            for(var entry:pose.getJointTransformData().entrySet()) {
                var before=original.orElseEmpty(entry.getKey());var after=entry.getValue();
                assertEquals(before.translation(),after.translation());
                assertEquals(before.scale(),after.scale());
            }
        }
    }
    @Test void gestureHasAnticipationOpeningAndCompleteRecovery() {
        var early=new BlacklightPose();WingTransformationPose.apply(early,true,6);
        var opening=new BlacklightPose();WingTransformationPose.apply(opening,true,20);
        assertTrue(early.get("Torso").rotation().angle()>.03);
        assertTrue(opening.get("Arm_R").rotation().angle()>.2);
        assertTrue(opening.get("Arm_R").rotation().getEulerAnglesXYZ(new Vector3f()).z<0);
        assertTrue(opening.get("Arm_L").rotation().getEulerAnglesXYZ(new Vector3f()).z>0);
        for(boolean deployed:new boolean[]{false,true}) {
            var pose=new BlacklightPose();WingTransformationPose.apply(pose,deployed,0);
            assertTrue(pose.getJointTransformData().isEmpty());
            WingTransformationPose.apply(pose,deployed,40);
            assertTrue(pose.getJointTransformData().isEmpty());
        }
    }
}
