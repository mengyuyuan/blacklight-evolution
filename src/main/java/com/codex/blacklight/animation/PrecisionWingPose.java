package com.codex.blacklight.animation;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A fixed body mount and two rigid fans. Every wing triangle uses one uniform
 * transform: armour cannot stretch or interpolate across unrelated finger bones. */
public final class PrecisionWingPose {
    public static final float GROUND_SCALE=.58F;
    public static Vector3f pivot(int part){return new Vector3f(part==1?.32F:-.32F,1.46F,.2F);}
    public static Matrix4f transform(int part,SovereignWingMotion.Pose motion,Vector3f strokeAxis,float deployment,float air,int order){
        deployment=finite(deployment);air=finite(air);
        if(part==0)return new Matrix4f().translation(0,1.36F,.2F).scale(deployment).translate(0,-1.36F,-.2F);
        boolean left=part==1;float side=left?1:-1;
        var wing=left?motion.left():motion.right();
        float stroke=(float)Math.toRadians(Math.clamp(wing.strokeDegrees()*.32F,-18,14));
        var flight=new Quaternionf().rotationAxis(stroke,strokeAxis)
                .rotateY((float)Math.toRadians(wing.root().y()*.42F))
                .rotateZ((float)Math.toRadians(wing.root().z()*.3F))
                .rotateX((float)Math.toRadians(wing.root().x()*.3F));
        boolean outer=left==((order&1)==0);
        var folded=new Quaternionf().rotationY((float)Math.toRadians(-side*(outer?110:130)));
        float unfurl=air*air*(3-2*air);
        var rotation=folded.slerp(flight,unfurl).normalize();
        float size=(GROUND_SCALE+(1-GROUND_SCALE)*unfurl)*WingMorphTimeline.growth(left?"left_root":"right_root",deployment);
        var root=pivot(part);
        return new Matrix4f().translation(root).rotate(rotation).scale(size).translate(-root.x,-root.y,-root.z);
    }
    private static float finite(float value){return Float.isFinite(value)?Math.clamp(value,0,1):0;}
    private PrecisionWingPose(){}
}
