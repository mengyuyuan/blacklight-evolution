package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.P1MilitaryMotion;
import com.codex.blacklight.client.model.BlackwatchRigPose.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Original foot trajectories on the native 6+5-pixel legs, with an independent boot joint.
 * Source translation is removed at 16 px/m (the server's root scale), so planted feet
 * do not inherit the actor's forward stride a second time. Native limb proportions
 * still require IK and a small pelvis adjustment; this is not the original skin. */
final class BlackwatchLegPose {
    private static final double SOURCE_SCALE=13,GROUND=24.05;
    private record Goal(Vec hip,Vec ankle,Vec knee,Quaterniond boot){}
    private record Leg(Vec hip,Vec thigh,Vec shin,Vec foot){}
    static Pose original(Pose base,P1MilitaryMotion.Frame frame,double influence){
        double weight=Double.isFinite(influence)?Math.max(0,Math.min(1,influence)):0;
        if(weight==0)return base;
        for(String joint:new String[]{"Pelvis","Knee_R","Knee_L","Ankle_R","Ankle_L"})if(frame.joint(joint)==null)return base;
        boolean mirror=base.leftHanded();
        Vec pelvis=delta(frame,"Pelvis",mirror).mul(weight);
        Goal right=goal(base,frame,true,mirror,weight,pelvis),left=goal(base,frame,false,mirror,weight,pelvis);
        // Lower the shared pelvis only enough to keep both fixed-length chains reachable.
        double drop=Math.max(requiredDrop(right),requiredDrop(left));drop=Math.min(6,Math.max(0,drop));
        Vec adjustment=pelvis.add(new Vec(0,drop,0));
        Leg r=solve(right,new Vec(0,drop,0)),l=solve(left,new Vec(0,drop,0));
        return new Pose(shift(base.right(),adjustment),shift(base.left(),adjustment),base.torsoPosition().add(adjustment),base.torsoRotation(),
                base.carryPosition(),base.carryRotation(),base.headPosition().add(adjustment),base.headRotation(),
                r.hip,r.thigh,l.hip,l.thigh,r.shin,l.shin,r.foot,l.foot,base.leftHanded(),base.supportWeight(),base.weight());
    }
    private static Goal goal(Pose base,P1MilitaryMotion.Frame frame,boolean right,boolean mirror,double weight,Vec pelvis){
        String side=right!=mirror?"R":"L";
        Vec oldHip=right?base.rightLegPosition():base.leftLegPosition();
        Quaterniond oldUpper=rotation(right?base.rightLegRotation():base.leftLegRotation());
        Quaterniond oldLower=new Quaterniond(oldUpper).mul(rotation(right?base.rightKneeRotation():base.leftKneeRotation()));
        Quaterniond oldBoot=new Quaterniond(oldLower).mul(rotation(right?base.rightFootRotation():base.leftFootRotation()));
        Vec oldKnee=oldHip.add(transform(oldUpper,new Vec(0,6,0)));
        Vec oldAnkle=oldKnee.add(transform(oldLower,new Vec(0,5,0)));
        Vec sourceAnkle=new Vec(right?-1.9:1.9,23,0).add(delta(frame,"Ankle_"+side,mirror));
        Vec sourceKnee=new Vec(right?-1.9:1.9,18,0).add(delta(frame,"Knee_"+side,mirror));
        Quaterniond boot=oldBoot.slerp(sourceRotation(frame,"Ankle_"+side,mirror),weight).normalize();
        Vec ankle=mix(oldAnkle,sourceAnkle,weight);
        // Keep the complete sole above the actor's supporting plane, including toe roll.
        ankle=new Vec(ankle.x(),Math.min(ankle.y(),GROUND-lowestSole(boot)),ankle.z());
        Vec socket=base.torsoPosition().add(transform(rotation(base.torsoRotation()),new Vec(right?-1.9:1.9,12,0)));
        return new Goal(mix(oldHip,socket,weight).add(pelvis),ankle,mix(oldKnee,sourceKnee,weight),boot);
    }
    private static double requiredDrop(Goal goal){
        Vec d=goal.ankle.sub(goal.hip);double horizontal=d.x()*d.x()+d.z()*d.z();
        return d.y()-Math.sqrt(Math.max(.01,10.999*10.999-horizontal));
    }
    private static Leg solve(Goal goal,Vec drop){
        Vec hip=goal.hip.add(drop),d=goal.ankle.sub(hip);double length=Math.max(1.001,Math.min(10.999,d.length()));Vec axis=d.normal();
        double along=(36-25+length*length)/(2*length),height=Math.sqrt(Math.max(0,36-along*along));
        Vec hint=goal.knee.sub(hip),bend=hint.sub(axis.mul(hint.dot(axis)));
        if(bend.length()<1e-5){hint=new Vec(0,0,-1);bend=hint.sub(axis.mul(hint.dot(axis)));}
        Vec knee=hip.add(axis.mul(along)).add(bend.normal().mul(height));
        Vec ankle=hip.add(axis.mul(length));
        Quaterniond thigh=pointDown(knee.sub(hip)),lower=pointDown(ankle.sub(knee));
        return new Leg(hip,angles(thigh),angles(new Quaterniond(thigh).invert().mul(lower)),angles(new Quaterniond(lower).invert().mul(goal.boot)));
    }
    private static Vec delta(P1MilitaryMotion.Frame frame,String joint,boolean mirror){
        var p=new Vector3d(frame.joint(joint).position()).sub(frame.rest(joint).position());
        var root=new Vector3d(frame.joint("Motion_Root").position()).sub(frame.rest("Motion_Root").position());
        return new Vec((p.x*SOURCE_SCALE-root.x*16)*(mirror?-1:1),-(p.y-root.y)*SOURCE_SCALE,p.z*SOURCE_SCALE-root.z*16);
    }
    private static Quaterniond sourceRotation(P1MilitaryMotion.Frame frame,String joint,boolean mirror){
        var q=new Quaterniond(frame.joint(joint).rotation()).mul(new Quaterniond(frame.rest(joint).rotation()).invert());
        q.set(-q.x,q.y,-q.z,q.w);if(mirror)q.set(q.x,-q.y,-q.z,q.w);return q.normalize();
    }
    private static double lowestSole(Quaterniond boot){
        double result=-Double.MAX_VALUE;
        for(double x:new double[]{-2.1,2.1})for(double y:new double[]{.45,1.05})for(double z:new double[]{-2.55,2.2})result=Math.max(result,transform(boot,new Vec(x,y,z)).y());
        return result;
    }
    private static Quaterniond pointDown(Vec direction){return new Quaterniond().rotationTo(new Vector3d(0,1,0),new Vector3d(direction.x(),direction.y(),direction.z()).normalize());}
    private static Quaterniond rotation(Vec r){return new Quaterniond().rotationZYX(r.z(),r.y(),r.x());}
    private static Vec angles(Quaterniond q){
        var m=new org.joml.Matrix3d().set(q);double y=Math.asin(Math.max(-1,Math.min(1,-m.m02())));
        return Math.abs(Math.cos(y))>1e-7?new Vec(Math.atan2(m.m12(),m.m22()),y,Math.atan2(m.m01(),m.m00())):new Vec(0,y,Math.atan2(-m.m10(),m.m11()));
    }
    private static Vec transform(Quaterniond q,Vec p){var v=q.transform(new Vector3d(p.x(),p.y(),p.z()));return new Vec(v.x,v.y,v.z);}
    private static Vec mix(Vec a,Vec b,double t){return a.add(b.sub(a).mul(t));}
    private static Arm shift(Arm arm,Vec delta){return new Arm(arm.shoulder().add(delta),arm.upper(),arm.forearm());}
    private BlackwatchLegPose(){}
}
