package com.codex.blacklight.combat;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoddessGestureTest {
    private static Vector3f point(Matrix4f bone) {
        return bone.getTranslation(new Vector3f());
    }

    @Test void curlingOneFingerPreservesItsJointSpacingAndTheOtherFingers() {
        for(int arm:new int[]{5,19}){
            Matrix4f[] b=new Matrix4f[GoddessRig.JOINTS];
            for(int j=0;j<b.length;j++)b[j]=GoddessRig.idle(j,17);
            var before=java.util.Arrays.stream(b).map(Matrix4f::new).toArray(Matrix4f[]::new);
            GoddessGesture.digits(b,arm,.96F,0,0,0);
            for(int j=0;j<b.length;j++){
                if(j<arm+3 || j>arm+5)assertTrue(before[j].equals(b[j],1e-6F),"adjacent chain "+j);
            }
            for(int j=arm+3;j<arm+5;j++)assertEquals(point(before[j]).distance(point(before[j+1])),
                    point(b[j]).distance(point(b[j+1])),2e-5F);
            assertTrue(point(before[arm+5]).distance(point(b[arm+5]))>.12F);
        }
    }

    @Test void fallBlendRotatesWithoutCollapsingASegmentAndRetainsExactContact() {
        var upright=new Matrix4f().translation(0,4,0);
        var prone=new Matrix4f().translation(0,0,3).rotateX(2.53F);
        assertSame(upright,GoddessRig.blendPose(upright,prone,0));
        assertSame(prone,GoddessRig.blendPose(upright,prone,1));
        for(float t=.05F;t<1;t+=.10F){
            var mid=GoddessRig.blendPose(upright,prone,t);
            assertEquals(1,mid.determinant3x3(),1e-5F);
            assertEquals(1,mid.transformDirection(new Vector3f(0,1,0)).length(),1e-5F);
        }
    }
}

