package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Outer fan transforms share the classic motion; the model pins its soft collar
 * to the body. Armour never interpolates across unrelated finger bones. */
public final class PrecisionWingPose {
    public static final float GROUND_SCALE=.58F;
    /** Only the soft proximal collar yields; the outboard armour stays rigid. */
    public static float flightWeight(float x,float y,float z){
        float dx=Math.abs(x)-.32F,dy=y-1.46F,dz=z-.2F;
        // Distance also releases the long inner blades: their tips can share
        // the collar's X coordinate while being far below the shoulder.
        float distance=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        float t=com.codex.blacklight.forge.WingMath.clamp((Math.max(Math.abs(x),distance)-.65F)/.40F,0,1);
        return t*t*(3-2*t);
    }
    public static Vector3f pivot(int part){return new Vector3f(part==1?.32F:-.32F,1.46F,.2F);}
    public static Matrix4f transform(int part,SovereignWingMotion.Pose motion,Vector3f strokeAxis,float deployment,float air,int order){
        deployment=finite(deployment);air=finite(air);
        if(part==0)return new Matrix4f().translation(0,1.36F,.2F).scale(deployment).translate(0,-1.36F,-.2F);
        boolean left=part==1;float side=left?1:-1;
        // Both appearances use the same shoulder motion. Attenuating and clipping
        // this pose flattened the power stroke and weakened turns on the precision
        // mesh, despite the shared animation clock. Keep its accepted rigid shape
        // and ground fold, but do not retime or rescale the flight articulation.
        var flight=SovereignWingMotion.rootRotation(left?motion.left():motion.right(),strokeAxis);
        // The classic fan is opened by its elbow and fingers as well as its
        // shoulder. Retarget the central spar's orientation onto the rigid fan;
        // omitting this chain leaves the long blades dangling under the actor.
        String sideName=left?"left":"right";
        for(String joint:new String[]{"rib_2","arch_2","finger_2","tip_2"})
            flight.mul(SovereignWingMotion.articulation(sideName+"_"+joint,motion,strokeAxis,1,new Quaternionf()));
        boolean outer=left==((order&1)==0);
        var folded=new Quaternionf().rotationY((float)Math.toRadians(-side*(outer?110:130)));
        float unfurl=air*air*(3-2*air);
        var rotation=folded.slerp(flight,unfurl).normalize();
        float size=(GROUND_SCALE+(1-GROUND_SCALE)*unfurl)*WingMorphTimeline.growth(left?"left_root":"right_root",deployment);
        var root=pivot(part);
        return new Matrix4f().translation(root).rotate(rotation).scale(size).translate(-root.x,-root.y,-root.z);
    }
    private static float finite(float value){return Float.isFinite(value)?com.codex.blacklight.forge.WingMath.clamp(value,0,1):0;}
    private PrecisionWingPose(){}
}
