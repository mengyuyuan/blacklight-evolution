package com.codex.blacklight.client.renderer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoneWorldPointTest {
    @Test void trailAnchorDoesNotOrbitWhenCameraTurns() {
        var local=new Vector3f(0,1.05F,.1F);
        var model=new Matrix4f().translation(2,-1,4).rotateY(.7F).scale(-1,1,1);
        var expected=model.transformPosition(new Vector3f(local));
        for(float yaw:new float[]{0,.8F,1.7F,3.14F})for(float pitch:new float[]{-.8F,0,.6F}) {
            var view=new Matrix4f().rotateX(pitch).rotateY(yaw).rotateZ(.08F);
            var combined=new Matrix4f(view).mul(model);
            var actual=BlacklightBoneRendering.entityWorldRelative(combined,local,new Matrix3f(view).invert());
            assertEquals(0,expected.distance(actual),.00001);
        }
    }
}
