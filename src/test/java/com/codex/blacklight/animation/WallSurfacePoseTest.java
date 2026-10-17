package com.codex.blacklight.animation;

import org.junit.jupiter.api.Test;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class WallSurfacePoseTest {
    private static final String[] RUNS={"basic_wall_run_up","basic_wall_run_left","basic_wall_run_right",
            "basic_wall_run_diag_left","basic_wall_run_diag_right"};
    @Test void runningStanceStandsOutFromTheWallAndPreservesAuthoredStride() {
        var rig=BlacklightSkeleton.get();
        for(String key:RUNS)for(int frame=0;frame<40;frame++) {
            var pose=MotionLibrary.pose(key,MotionLibrary.duration(key)*frame/40F,true);
            var before=rig.getPoseMatrices(pose);
            double right=key.contains("left")?-1:key.contains("right")?1:0;
            double up=key.contains("diag")||right==0?1:0;
            WallSurfacePose.orientRun(pose,rig,right,up,.3F);
            var after=rig.getPoseMatrices(pose);
            assertEquals(rig.rootJoint.getLocalTransform().m31(),after[rig.rootJoint.getId()].m31(),.0001,
                    "wall stance must retain the body's vertical anchor instead of sinking below the floor");
            for(var foot:new BlacklightSkeleton.Bone[]{rig.legR,rig.legL}) {
                var sole=after[foot.getId()].transformPosition(new Vector3f(0,.43F,0));
                assertTrue(sole.y>0,key+" trailing foot must remain above the base floor at wall entry");
            }
            for(var bone:rig.bones()) {
                assertEquals(before[bone.getId()].getTranslation(new Vector3f()).distance(before[rig.rootJoint.getId()].getTranslation(new Vector3f())),
                        after[bone.getId()].getTranslation(new Vector3f()).distance(after[rig.rootJoint.getId()].getTranslation(new Vector3f())),.0001);
            }
            var torso=after[rig.head.getId()].getTranslation(new Vector3f()).sub(after[rig.rootJoint.getId()].getTranslation(new Vector3f()));
            assertTrue(torso.z/torso.length()>.72F,key+" torso must stand away from the wall, not crouch parallel to it");
            float soleZ=Float.POSITIVE_INFINITY;
            for(var foot:new BlacklightSkeleton.Bone[]{rig.legR,rig.legL})
                for(float x:new float[]{-.155F,.155F})for(float z:new float[]{-.155F,.155F})
                    soleZ=Math.min(soleZ,after[foot.getId()].transformPosition(new Vector3f(x,.43F,z)).z);
            assertEquals(-.3F/.9375F+.025F,soleZ,.0001,"at least one sole must reach the wall rather than both feet floating");
            WallSurfacePose.clearSkin(pose,rig,.3F);
            assertTrue(WallSurfacePose.skinMinimumZ(pose,rig)>=-.296F,key+" contact skin stays outside wall");
        }
    }
    @Test void allWallNormalsKeepTheTorsoOutsideRegardlessOfCameraYaw() {
        var rig=BlacklightSkeleton.get();
        for(float yaw:new float[]{0,37,90,180,270})for(var normal:new Vector3f[]{
                new Vector3f(1,0,0),new Vector3f(-1,0,0),new Vector3f(0,0,1),new Vector3f(0,0,-1)}) {
            for(double up:new double[]{1,-1}) {
                var pose=MotionLibrary.pose("basic_wall_run_up",.2F,true);
                WallSurfacePose.orientRun(pose,rig,0,up,.3F);
                WallSurfacePose.clearSkin(pose,rig,.3F);
                var model=new Matrix4f().rotationY((float)Math.toRadians(180-yaw)).scale(.9375F);
                WallSurfacePose.faceWall(pose,rig,model,normal.x,normal.z);
                var head=new Matrix4f(model).mul(rig.getBoundTransformFor(pose,rig.head)).getTranslation(new Vector3f());
                var hip=new Matrix4f(model).mul(rig.getBoundTransformFor(pose,rig.rootJoint)).getTranslation(new Vector3f());
                assertTrue(head.sub(hip).dot(normal)>.2F,"body must stand away from wall even running down or turning camera");
            }
        }
    }
    @Test void stoppedGravityDoesNotBecomeADownwardRunningCommand() {
        assertEquals(0,WallSurfacePose.normalizeRunVertical(0,-.0784,true,false));
        assertEquals(0,WallSurfacePose.normalizeRunVertical(.01,-.0784,false,true));
    }
    @Test void actualWallTravelKeepsItsDirection() {
        assertEquals(-.4,WallSurfacePose.normalizeRunVertical(0,-.4,false,false));
        assertEquals(-.4,WallSurfacePose.normalizeRunVertical(0,-.4,true,false));
        assertEquals(-.0784,WallSurfacePose.normalizeRunVertical(.3,-.0784,true,false));
        assertEquals(.55,WallSurfacePose.normalizeRunVertical(0,.55,false,false));
    }
}
