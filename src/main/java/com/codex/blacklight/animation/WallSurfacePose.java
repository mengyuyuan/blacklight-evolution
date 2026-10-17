package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Rigid wall-local placement shared by the renderer and server pose validation. */
public final class WallSurfacePose {
    private static final float PLAYER_SCALE=.9375F;
    private WallSurfacePose() {}
    /** Gravity during a stopped or retiring wall pose is not a downward running command. */
    public static double normalizeRunVertical(double right,double up,boolean grounded,boolean retiring) {
        return (grounded||retiring)&&Math.abs(right)<.018&&Math.abs(up)<.12?0:up;
    }
    public static void orientRun(BlacklightPose pose,BlacklightSkeleton skeleton,double right,double up,float distance) {
        float angle=(float)-Math.atan2(right,Math.abs(up)+Math.abs(right)<.001?1:up);
        // Treat the wall as the ground: authored +Y (height above the feet)
        // becomes outward +Z, and authored -Z travel becomes wall-local +Y.
        // Keep the pelvis at the body's bind height along the wall: rotating the
        // origin at the entity's feet drops the trailing stride through the floor.
        // Only the original ground height becomes wall-normal clearance.
        var hip=skeleton.getBoundTransformFor(pose,skeleton.rootJoint).getTranslation(new Vector3f());
        var torso=skeleton.getBoundTransformFor(pose,skeleton.head).getTranslation(new Vector3f()).sub(hip);
        // These wall-run clips already carry a deep forward root lean. A bare
        // quarter turn leaves that lean almost parallel to the wall even with
        // no climbing clip involved. Keep up to 35 degrees of running lean,
        // rotating the whole stance so shoulders, hips and feet stay connected.
        float excessiveLean=Math.max(0,(float)Math.atan2(-torso.z,torso.y)-(float)Math.toRadians(35));
        float anchorHeight=skeleton.rootJoint.getLocalTransform().m31();
        var surface=new Matrix4f().translation(hip.x,anchorHeight,hip.y-distance/PLAYER_SCALE)
                .rotateZ(angle).rotateX((float)Math.PI/2+excessiveLean).translate(-hip.x,-hip.y,-hip.z);
        transformRoot(pose,skeleton,surface);
        // Plant the nearest sole at the wall plane rather than moving the body
        // outward and leaving both feet floating. Final skin clearance can still
        // move the whole pose away from a wall if an extremity intersects it.
        float soleZ=Float.POSITIVE_INFINITY;
        for(var foot:new BlacklightSkeleton.Bone[]{skeleton.legR,skeleton.legL}) {
            var matrix=skeleton.getBoundTransformFor(pose,foot);
            for(float x:new float[]{-.155F,.155F})for(float z:new float[]{-.155F,.155F})
                soleZ=Math.min(soleZ,matrix.transformPosition(new Vector3f(x,.43F,z)).z);
        }
        translate(pose,skeleton,0,0,-distance/PLAYER_SCALE+.025F-soleZ);
    }
    public static float headMinimumZ(BlacklightPose pose,BlacklightSkeleton skeleton) {
        var head=skeleton.getBoundTransformFor(pose,skeleton.head);
        float minimum=Float.POSITIVE_INFINITY;
        for(float x:new float[]{-.275F,.275F})for(float y:new float[]{-.025F,.525F})for(float z:new float[]{-.275F,.275F})
            minimum=Math.min(minimum,head.m02()*x+head.m12()*y+head.m22()*z+head.m32());
        return minimum;
    }
    public static void clearHead(BlacklightPose pose,BlacklightSkeleton skeleton,float distance) {
        float shift=-distance/PLAYER_SCALE+.025F-headMinimumZ(pose,skeleton);
        if(shift>0)translate(pose,skeleton,0,0,shift);
    }
    public static float skinMinimumZ(BlacklightPose pose,BlacklightSkeleton skeleton) {
        float minimum=headMinimumZ(pose,skeleton);
        for(String name:new String[]{"Thigh_R","Leg_R","Thigh_L","Leg_L","Arm_R","Hand_R","Arm_L","Hand_L"}) {
            var joint=skeleton.searchJointByName(name);if(joint==null)continue;
            var matrix=skeleton.getBoundTransformFor(pose,joint);
            boolean leg=name.contains("Leg")||name.contains("Thigh");
            float radius=leg?.155F:.13F,length=leg?.43F:.32F;
            for(float x:new float[]{-radius,radius})for(float y:new float[]{0,length})for(float z:new float[]{-radius,radius})
                minimum=Math.min(minimum,matrix.m02()*x+matrix.m12()*y+matrix.m22()*z+matrix.m32());
        }
        return minimum;
    }
    /** Translate the complete posed skin; preserve the authored stride and arm counterbalance. */
    public static void clearSkin(BlacklightPose pose,BlacklightSkeleton skeleton,float distance) {
        float shift=-distance/PLAYER_SCALE+.025F-skinMinimumZ(pose,skeleton);
        if(shift>0)translate(pose,skeleton,0,0,shift);
    }
    public static void faceWall(BlacklightPose pose,BlacklightSkeleton skeleton,Matrix4f renderModel,double nx,double nz) {
        var inverse=new Matrix4f(renderModel).invert();
        var normal=inverse.transformDirection(new Vector3f((float)nx,0,(float)nz));
        transformRoot(pose,skeleton,new Matrix4f().rotationY((float)Math.atan2(normal.x,normal.z)));
    }
    public static void translate(BlacklightPose pose,BlacklightSkeleton skeleton,float x,float y,float z) {
        var delta=new Matrix4f(skeleton.rootJoint.getLocalTransform()).invert().transformDirection(new Vector3f(x,y,z));
        pose.orElseEmpty("Root").translation().add(delta);
    }
    private static void transformRoot(BlacklightPose pose,BlacklightSkeleton skeleton,Matrix4f transform) {
        var bind=skeleton.rootJoint.getLocalTransform();
        var world=new Matrix4f(bind).mul(pose.orElseEmpty("Root").toMatrix());
        var local=new Matrix4f(bind).invert().mul(transform).mul(world);
        pose.getJointTransformData().put("Root",BoneTransform.fromMatrix(local));
    }
}
