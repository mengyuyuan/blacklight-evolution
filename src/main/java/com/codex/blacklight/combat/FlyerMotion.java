package com.codex.blacklight.combat;

import com.codex.blacklight.entity.*;
import org.joml.Quaternionf;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/** Verified P2 skeletal curves. Controller phase lengths are explicit MC tuning. */
public final class FlyerMotion {
    public static final int IDLE=0,PREPARE=1,BOOST=2,RAM=3,RECOVER=4,COLLISION=5,HIT_FRONT=6,HIT_BACK=7;
    public static SourceBossMotion table(){return SourceBossMotion.get(EncounterKind.FLYER);}
    public static String clip(int stage){return switch(stage){case PREPARE->"idle";case BOOST->"boost";case RAM->"ram";case RECOVER->"glide";case COLLISION->"collision";case HIT_FRONT->"hit_front";case HIT_BACK->"hit_back";default->"flap";};}
    public static int duration(int stage){return switch(stage){case PREPARE->14;case RECOVER->28;case IDLE->Integer.MAX_VALUE;default->table().ticks(clip(stage));};}
    private static float[][] sample(int stage,float age){return table().sample(clip(stage),age,stage==IDLE||stage==PREPARE||stage==RECOVER);}
    public static float[][] pose(int stage,float age,int previous,float previousAge,float pitch){
        var pose=sample(stage,age);
        if(age<4){var before=sample(previous,previousAge+age);for(int i=0;i<pose.length;i++)pose[i]=SourceBossMotion.blend(before[i],pose[i],Math.max(0,age/4));}
        // Flight pitch rotates about the thorax rather than a point below the body.
        var p=pose[0];var q=new Quaternionf().rotationX((float)Math.toRadians(-pitch));
        var rotation=new Matrix3f().rotation(q).mul(new Matrix3f().rotationZYX(p[2],p[1],p[0]));
        p[0]=(float)Math.atan2(rotation.m12(),rotation.m22());p[1]=(float)Math.asin(Math.max(-1,Math.min(1,-rotation.m02())));p[2]=(float)Math.atan2(rotation.m01(),rotation.m00());
        var shifted=q.transform(new Vector3f(p[3],p[4]-10,p[5]));p[3]=shifted.x;p[4]=shifted.y+10;p[5]=shifted.z;
        return pose;
    }
    private FlyerMotion(){}
}
