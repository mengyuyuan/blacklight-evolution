package com.codex.blacklight.animation;
import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;
class RigidSkinningTest {
    private Vector3f skin(Matrix4f a,Matrix4f b,float w,Vector3f p){
        return RigidSkinning.transform(RigidSkinning.Joint.from(a),RigidSkinning.Joint.from(b),w,p,
                new Vector3f(),new Quaternionf(),new Quaternionf());
    }
    @Test void halfTurnDoesNotCrushJointRadius(){
        var a=new Matrix4f();var b=new Matrix4f().rotateY((float)Math.PI);
        assertEquals(1,skin(a,b,.5F,new Vector3f(1,0,0)).length(),1e-5);
    }
    @Test void endpointsAndOffsetPivotArePreserved(){
        var a=new Matrix4f().translation(2,3,4).rotateX(.3F);
        var b=new Matrix4f().translation(-1,2,3).rotateZ(1.7F);
        var p=new Vector3f(1,2,3);
        assertTrue(skin(a,b,0,p).distance(a.transformPosition(p,new Vector3f()))<1e-5);
        assertTrue(skin(a,b,1,p).distance(b.transformPosition(p,new Vector3f()))<1e-5);
        var pivot=new Vector3f(2,3,4);var rotation=new Matrix4f().translate(pivot).rotateY(2).translate(new Vector3f(pivot).negate());
        assertTrue(skin(new Matrix4f(),rotation,.5F,pivot).distance(pivot)<1e-5);
    }
}
