package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.BlacklightSkeleton;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeatdownMountPoseTest {
    @Test void pelvisSettlesOnTheRequestedSurfaceEvenWhenTheAuthoredRootHasMoved() {
        var rig = BlacklightSkeleton.get();
        var pose = new BlacklightPose();
        var root = pose.orElseEmpty(rig.rootJoint.getName());
        root.translation().set(.18F,-.22F,-.41F);
        root.rotation().rotateY(.8F);
        var rotation = new org.joml.Quaternionf(root.rotation());
        var seat = new Vector3f(-.21F,.47F,.72F);
        BeatdownMountPose.seatPelvis(rig,pose,seat);
        assertTrue(seat.distance(rig.getBoundTransformFor(pose,rig.rootJoint).getTranslation(new Vector3f())) < 1e-5F);
        assertEquals(rotation,root.rotation());
    }

    @Test void kneesClearBothFlanksWithFixedLengthThighs() {
        var seat = new Vector3f(-.15F,.56F,.7F);
        for (float yaw : new float[]{0F,.7F,1.57F}) {
            var forward = new Vector3f(0,0,1).rotateY(yaw);
            for (int side : new int[]{-1,1}) {
                var outward = new Vector3f(side,0,0).rotateY(yaw);
                var hip = new Vector3f(seat).fma(.12F,outward);
                for (float width : new float[]{.22F,.27F,.36F}) {
                    var knee = BeatdownMountPose.kneePosition(hip,seat,outward,forward,.375F,width);
                    assertEquals(.375F,knee.distance(hip),1e-5F);
                    assertTrue(new Vector3f(knee).sub(seat).dot(outward) > width+.095F);
                    assertTrue(knee.y < hip.y);
                    assertTrue(new Vector3f(knee).sub(hip).dot(forward) > 0);
                }
            }
        }
    }
}
