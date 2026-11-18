package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagdollHingeTest {
    private static final float MIN=(float)Math.toRadians(-155);
    private static void same(Quaternionf expected,Quaternionf actual){
        assertTrue(actual.isFinite());assertEquals(1,actual.lengthSquared(),1e-5);
        for(var axis:new Vector3f[]{new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)})
            assertTrue(expected.transform(new Vector3f(axis)).distance(actual.transform(new Vector3f(axis)))<2e-5);
    }
    @Test void legalFlexionSurvivesArbitraryCapturePoseAndBodyRotation(){
        var upperBind=new Quaternionf().rotationYXZ(1.7F,.6F,-.35F);
        var lowerBind=new Quaternionf(upperBind).rotateX(-.8F);
        var upperDelta=new Quaternionf().rotationZYX(-.9F,1.2F,.4F);
        var hinge=new RagdollHinge(upperBind,lowerBind,MIN,0);
        for(int degrees=-155;degrees<=0;degrees++){
            var wanted=new Quaternionf(upperDelta).mul(upperBind).rotateX((float)Math.toRadians(degrees)).mul(new Quaternionf(lowerBind).conjugate());
            same(wanted,hinge.constrain(upperDelta,wanted));
        }
    }
    @Test void backwardBendAndSidewaysSwingCannotBecomeLegalKneeMotion(){
        var hinge=new RagdollHinge(new Quaternionf(),new Quaternionf(),MIN,0);
        same(new Quaternionf(),hinge.constrain(new Quaternionf(),new Quaternionf().rotationX(.5F)));
        same(new Quaternionf().rotationX(MIN),hinge.constrain(new Quaternionf(),new Quaternionf().rotationX(-3)));
        var twisted=new Quaternionf().rotationY(.9F).rotateX(-1.2F);
        same(new Quaternionf().rotationX(-1.2F),hinge.constrain(new Quaternionf(),twisted));
    }
    @Test void equivalentQuaternionSignsAndSingularSwingStayFinite(){
        var hinge=new RagdollHinge(new Quaternionf(),new Quaternionf(),MIN,0);
        for(float a=-3.14F;a<3.15F;a+=.031F){var q=new Quaternionf().rotationY(.4F).rotateX(a);same(hinge.constrain(new Quaternionf(),q),hinge.constrain(new Quaternionf(),new Quaternionf(q).mul(-1)));}
        same(new Quaternionf(),hinge.constrain(new Quaternionf(),new Quaternionf(0,1,0,0)));
    }
    @Test void angularVelocityFollowsParentButCannotDriveIntoTheStops(){
        var bind=new Quaternionf().rotationZ((float)Math.PI/2);
        var hinge=new RagdollHinge(bind,bind,MIN,0);var parentSpin=new Vector3f(.2F,.3F,.4F);
        assertTrue(hinge.constrainSpin(new Quaternionf(),new Quaternionf(),parentSpin,new Vector3f(3,2,4)).distance(parentSpin)<1e-5);
        var inward=hinge.constrainSpin(new Quaternionf(),new Quaternionf(),parentSpin,new Vector3f(3,-2,4));
        assertEquals(.2F,inward.x,1e-5);assertEquals(-2,inward.y,1e-5);assertEquals(.4F,inward.z,1e-5);
        var lower=new Quaternionf(bind).rotateX(MIN).mul(new Quaternionf(bind).conjugate());
        assertTrue(hinge.constrainSpin(new Quaternionf(),lower,parentSpin,new Vector3f(3,-2,4)).distance(parentSpin)<1e-5);
    }
    @Test void orientationCorrectionPreservesTheRealJointPivotAtAnyScale(){
        var at=new Vec3(3,251,-7);var local=new Vec3(.14,.73,-.12);
        var before=new Quaternionf().rotationXYZ(.2F,.8F,-.4F);var after=new Quaternionf().rotationX(-1.4F);
        for(float scale:new float[]{.15F,1,2}){
            var pivot=at.add(new Vec3(before.transform(local.scale(scale).toVector3f())));
            var moved=at.add(RagdollHinge.pivotShift(local,before,after,scale));
            assertTrue(pivot.distanceTo(moved.add(new Vec3(after.transform(local.scale(scale).toVector3f()))))<1e-6);
        }
    }
}
