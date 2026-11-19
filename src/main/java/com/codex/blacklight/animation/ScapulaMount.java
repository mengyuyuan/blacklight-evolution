package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Geometry uses the vanilla player's metre-sized bind space, on the anatomical left. */
public final class ScapulaMount {
    private static final BlacklightSkeleton RIG = BlacklightSkeleton.get();
    private static final Matrix4f[] BIND = RIG.getPoseMatrices(new BlacklightPose());
    private static final Matrix4f CHEST_INVERSE = new Matrix4f(BIND[RIG.chest.getId()]).invert();
    private static final Matrix4f ARM_INVERSE = new Matrix4f(BIND[RIG.armL.getId()]).invert();
    private static final Matrix4f RIGHT_ARM_INVERSE = new Matrix4f(BIND[RIG.armR.getId()]).invert();
    public static final int OPEN_FRAME = 16;
    private ScapulaMount() {}

    public static Matrix4f chest(Matrix4f[] pose) {
        return new Matrix4f(pose[RIG.chest.getId()]).mul(CHEST_INVERSE);
    }
    public static Matrix4f sided(Matrix4f frame,boolean right) {
        return right?new Matrix4f(frame).scale(-1,1,1):frame;
    }
    public static Matrix4f arm(Matrix4f[] pose){return arm(pose,false);}
    public static Matrix4f arm(Matrix4f[] pose,boolean right) {
        return right?new Matrix4f(pose[RIG.armR.getId()]).mul(RIGHT_ARM_INVERSE).scale(-1,1,1)
                :new Matrix4f(pose[RIG.armL.getId()]).mul(ARM_INVERSE);
    }
    public static Matrix4f hinge(Matrix4f chest) {
        return new Matrix4f(chest).translate(-.58F,1.52F,.29F);
    }
    public static Vector3f bladeCenter() { return new Vector3f(-.216736F,.142674F,-.609514F); }
    public static Vector3f bladeDirection() { return new Vector3f(.086447F,-.127237F,-.988098F).normalize(); }
    private static final Vector3f[] ROOTS_OF_QUILLS={new Vector3f(0.259709F,-0.344652F,0.226529F),new Vector3f(0.259107F,-0.344082F,0.226408F),new Vector3f(0.239432F,-0.325480F,0.222428F),new Vector3f(0.196287F,-0.284689F,0.213241F),new Vector3f(0.135540F,-0.227255F,0.197747F),new Vector3f(0.063058F,-0.158727F,0.172501F),new Vector3f(-0.015281F,-0.084537F,0.133886F),new Vector3f(-0.093317F,-0.007847F,0.079091F),new Vector3f(-0.164664F,0.065254F,0.013293F),new Vector3f(-0.222824F,0.126663F,-0.052820F),new Vector3f(-0.261598F,0.168294F,-0.107678F),new Vector3f(-0.275412F,0.183082F,-0.141056F),new Vector3f(-0.269558F,0.175424F,-0.154046F),new Vector3f(-0.263622F,0.164703F,-0.162407F),new Vector3f(-0.259070F,0.154239F,-0.166645F),new Vector3f(-0.256288F,0.146609F,-0.168041F),new Vector3f(-0.255342F,0.143738F,-0.168246F)};
    /** Growth is measured from the mouth of this frame's sheath, not from the character's shoulder joint. */
    public static Matrix4f rootedGrowth(Matrix4f socket,int frame,float growth){
        var p=ROOTS_OF_QUILLS[frame];return new Matrix4f(socket).translate(p).scale(growth).translate(-p.x,-p.y,-p.z);
    }
    public static int frame(float open) { return Math.max(0,Math.min(OPEN_FRAME,Math.round(open*OPEN_FRAME))); }
}
