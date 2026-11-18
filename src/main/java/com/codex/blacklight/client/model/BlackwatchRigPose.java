package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.P1MilitaryMotion;

/** Pure retargeting math. Units are model pixels; rotations are native ZYX radians. */
public final class BlackwatchRigPose {
    public record Vec(double x,double y,double z){
        Vec add(Vec b){return new Vec(x+b.x,y+b.y,z+b.z);}Vec sub(Vec b){return new Vec(x-b.x,y-b.y,z-b.z);}
        Vec mul(double s){return new Vec(x*s,y*s,z*s);}double dot(Vec b){return x*b.x+y*b.y+z*b.z;}
        Vec cross(Vec b){return new Vec(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x);}double length(){return Math.sqrt(dot(this));}
        Vec normal(){double n=length();return n>1e-9?mul(1/n):new Vec(0,1,0);}Vec mirror(){return new Vec(-x,y,z);}
    }
    public record Arm(Vec shoulder,Vec upper,Vec forearm){}
    public record Pose(Arm right,Arm left,Vec torsoPosition,Vec torsoRotation,Vec carryPosition,Vec carryRotation,
                       Vec headPosition,Vec headRotation,Vec rightLegPosition,Vec rightLegRotation,Vec leftLegPosition,Vec leftLegRotation,
                       Vec rightKneeRotation,Vec leftKneeRotation,Vec rightFootRotation,Vec leftFootRotation,
                       boolean leftHanded,double supportWeight,double weight){}
    private record Matrix(double[] v){
        Vec point(Vec p){return new Vec(v[0]*p.x+v[1]*p.y+v[2]*p.z,v[3]*p.x+v[4]*p.y+v[5]*p.z,v[6]*p.x+v[7]*p.y+v[8]*p.z);}
        Matrix mul(Matrix b){double[] r=new double[9];for(int y=0;y<3;y++)for(int x=0;x<3;x++)for(int k=0;k<3;k++)r[y*3+x]+=v[y*3+k]*b.v[k*3+x];return new Matrix(r);}
        Matrix transpose(){return new Matrix(new double[]{v[0],v[3],v[6],v[1],v[4],v[7],v[2],v[5],v[8]});}
        Matrix mirror(){return new Matrix(new double[]{v[0],-v[1],-v[2],-v[3],v[4],v[5],-v[6],v[7],v[8]});}
        Vec angles(){double y=Math.asin(Math.max(-1,Math.min(1,-v[6])));return Math.abs(Math.cos(y))>1e-7?new Vec(Math.atan2(v[7],v[8]),y,Math.atan2(v[3],v[0])):new Vec(0,y,Math.atan2(-v[1],v[4]));}
    }
    private record Reference(Vec shoulder,Matrix upper,Matrix lower,Vec elbow,Vec wrist){}
    private static final BlackwatchReferenceProfile PROFILE=BlackwatchReferenceProfile.load();
    private static final Vec ZERO=new Vec(0,0,0),SEGMENT=new Vec(0,6,0),WAIST=new Vec(0,12,0),CARRY=vec(PROFILE.vector("carry_origin"));
    // The author's negative-X "Left Arm" becomes the native right arm, not the other way round.
    private static final Reference MAIN=reference("Left",new Vec(-6,0,0)),SUPPORT=reference("Right",new Vec(6,0,0));
    private BlackwatchRigPose(){}
    private static Vec vec(double[] p){return new Vec(p[0],p[1],p[2]);}
    private static Matrix rotation(Vec r){double cx=Math.cos(r.x),sx=Math.sin(r.x),cy=Math.cos(r.y),sy=Math.sin(r.y),cz=Math.cos(r.z),sz=Math.sin(r.z);return new Matrix(new double[]{cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx,sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx,-sy,cy*sx,cy*cx});}
    private static Vec nativeDegrees(double[] a){return new Vec(Math.toRadians(-a[0]),Math.toRadians(a[1]),Math.toRadians(-a[2]));}
    private static Reference reference(String name,Vec shoulder){var upper=rotation(nativeDegrees(PROFILE.boneRotation(name+" Arm")));var lower=rotation(nativeDegrees(PROFILE.boneRotation(name+" Arm Forearm")));var elbow=shoulder.add(upper.point(SEGMENT));return new Reference(shoulder,upper,lower,elbow,elbow.add(upper.mul(lower).point(SEGMENT)));}
    private static double clamp(double x){return Double.isFinite(x)?Math.max(0,Math.min(1,x)):0;}
    private static Vec mix(Vec a,Vec b,double t){return a.add(b.sub(a).mul(t));}
    private static Matrix between(Vec from,Vec to){
        Vec a=from.normal(),b=to.normal(),axis=a.cross(b);double c=Math.max(-1,Math.min(1,a.dot(b))),s=axis.length();
        if(s<1e-8)return rotation(ZERO);axis=axis.mul(1/s);double t=1-c,x=axis.x,y=axis.y,z=axis.z;
        return new Matrix(new double[]{t*x*x+c,t*x*y-s*z,t*x*z+s*y,t*x*y+s*z,t*y*y+c,t*y*z-s*x,t*x*z-s*y,t*y*z+s*x,t*z*z+c});
    }
    /** Bend towards a target while preserving the reference elbow plane and wrist twist. */
    private static Reference reach(Reference reference,Vec target){
        Vec delta=target.sub(reference.shoulder);double distance=Math.max(.05,Math.min(11.999,delta.length()));Vec direction=delta.normal();
        Vec bend=reference.elbow.sub(reference.shoulder).sub(direction.mul(reference.elbow.sub(reference.shoulder).dot(direction)));
        if(bend.length()<1e-6)bend=new Vec(0,0,-1).sub(direction.mul(direction.z*-1));
        Vec elbow=reference.shoulder.add(direction.mul(distance*.5)).add(bend.normal().mul(Math.sqrt(Math.max(0,36-distance*distance*.25))));
        Vec wrist=reference.shoulder.add(direction.mul(distance));
        Matrix upper=between(reference.elbow.sub(reference.shoulder),elbow.sub(reference.shoulder)).mul(reference.upper);
        Matrix lowerWorld=between(reference.wrist.sub(reference.elbow),wrist.sub(elbow)).mul(reference.upper.mul(reference.lower));
        return new Reference(reference.shoulder,upper,upper.transpose().mul(lowerWorld),elbow,wrist);
    }
    private static Reference mirror(Reference p){return new Reference(p.shoulder.mirror(),p.upper.mirror(),p.lower.mirror(),p.elbow.mirror(),p.wrist.mirror());}
    private static Vec fitCarry(Vec origin,Matrix carry,Reference main,Reference support){
        // A small translation keeps the same rigid weapon's two grips inside both fixed-shoulder reach spheres.
        // This is an explicit retargeting constraint, not an alteration of the author's animation curves.
        for(int pass=0;pass<12;pass++)for(var arm:new Reference[]{main,support}){
            Vec target=origin.add(carry.point(arm.wrist.sub(CARRY))),delta=target.sub(arm.shoulder);double length=delta.length();
            double limit=PROFILE.number("reach_limit");if(length>limit)origin=origin.sub(delta.mul((length-limit)/length));
        }return origin;
    }
    public static Pose solve(double yaw,double pitch,boolean leftHanded,double reloadProgress,double equippedWeight){
        return animated(yaw,pitch,leftHanded,reloadProgress,equippedWeight,0,0,0,Double.POSITIVE_INFINITY);
    }
    public static Pose animated(double yaw,double pitch,boolean leftHanded,double reloadProgress,double equippedWeight,
                                double seconds,double movement,double crouchWeight,double shotAge){
        return animated(yaw,pitch,leftHanded,reloadProgress,equippedWeight,seconds,movement,crouchWeight,shotAge,Double.POSITIVE_INFINITY);
    }
    public static Pose animated(double yaw,double pitch,boolean leftHanded,double reloadProgress,double equippedWeight,
                                double seconds,double movement,double crouchWeight,double shotAge,double meleeAge){
        return animated(yaw,pitch,leftHanded,reloadProgress,equippedWeight,seconds,movement,crouchWeight,shotAge,meleeAge,0);
    }
    public static Pose animated(double yaw,double pitch,boolean leftHanded,double reloadProgress,double equippedWeight,
                                double seconds,double movement,double crouchWeight,double shotAge,double meleeAge,double aimWeight){
        return animated(yaw,pitch,leftHanded,reloadProgress,equippedWeight,seconds,movement,crouchWeight,shotAge,meleeAge,aimWeight,Double.NaN,Double.NaN);
    }
    public static Pose animated(double yaw,double pitch,boolean leftHanded,double reloadProgress,double equippedWeight,
                                double seconds,double movement,double crouchWeight,double shotAge,double meleeAge,double aimWeight,double gaitPhase,double gaitRun){
        double weight=clamp(equippedWeight),move=clamp(movement*2),run=Double.isFinite(gaitRun)?clamp(gaitRun):clamp((movement-.55)/.3),crouch=clamp(crouchWeight);
        double walkClock=Double.isFinite(gaitPhase)?gaitPhase*PROFILE.duration("walk"):seconds,runClock=Double.isFinite(gaitPhase)?gaitPhase*PROFILE.duration("run"):seconds;
        Vec idleCarry=vec(PROFILE.sample("idle","Two Hand Carry","rotation",seconds));
        Vec carryAuth=mix(idleCarry,mix(vec(PROFILE.sample("walk","Two Hand Carry","rotation",walkClock)),vec(PROFILE.sample("run","Two Hand Carry","rotation",runClock)),run),move);
        Vec bodyAuth=mix(ZERO,mix(vec(PROFILE.sample("walk","Upper Body","rotation",walkClock)),vec(PROFILE.sample("run","Upper Body","rotation",runClock)),run),move);
        Vec rootAuth=mix(ZERO,mix(vec(PROFILE.sample("walk","Blackwatch Rig","position",walkClock)),vec(PROFILE.sample("run","Blackwatch Rig","position",runClock)),run),move);
        Vec carryPos=vec(PROFILE.sample("idle","Two Hand Carry","position",seconds)).mul(1-move);
        Vec headAuth=vec(PROFILE.sample("idle","Head","rotation",seconds)).mul(1-move).add(vec(PROFILE.sample("run","Head","rotation",runClock)).mul(move*run));
        double crouchClock=movement>.04?seconds:0;
        bodyAuth=mix(bodyAuth,vec(PROFILE.sample("crouch","Upper Body","rotation",crouchClock)),crouch);
        carryAuth=mix(carryAuth,vec(PROFILE.sample("crouch","Two Hand Carry","rotation",crouchClock)),crouch);
        rootAuth=mix(rootAuth,vec(PROFILE.sample("crouch","Blackwatch Rig","position",crouchClock)),crouch);
        headAuth=mix(headAuth,vec(PROFILE.sample("crouch","Head","rotation",crouchClock)),crouch);
        // Raise the carried weapon and cancel its authored across-chest mount yaw.
        // Both hands are still solved against the same rigid grips below.
        double aim=clamp(aimWeight);
        var aimBasis=rotation(vec(PROFILE.vector("weapon_mount_rotation"))).transpose();
        var aimAngles=(leftHanded?aimBasis.mirror():aimBasis).angles();
        carryAuth=mix(carryAuth,new Vec(-Math.toDegrees(aimAngles.x),Math.toDegrees(aimAngles.y),-Math.toDegrees(aimAngles.z)),aim);
        carryPos=carryPos.add(new Vec(0,3.25,0).mul(aim));
        if(Double.isFinite(shotAge)&&shotAge>=0&&shotAge<=.5){
            String shot=crouch>.5?"shoot_crouching":"shoot_standing";
            carryAuth=carryAuth.add(vec(PROFILE.sample(shot,"Two Hand Carry","rotation",shotAge)).sub(vec(PROFILE.sample(shot,"Two Hand Carry","rotation",0))));
            carryPos=carryPos.add(vec(PROFILE.sample(shot,"Two Hand Carry","position",shotAge)).sub(vec(PROFILE.sample(shot,"Two Hand Carry","position",0))));
        }
        // Authored stock-strike fallback for installations without the user's local P1 curves.
        if(Double.isFinite(meleeAge)&&meleeAge>=0&&meleeAge<=1){
            double wind=smooth(meleeAge/.30)*(1-smooth((meleeAge-.30)/.15));
            double strike=smooth((meleeAge-.30)/.15)*(1-smooth((meleeAge-.5)/.5));
            double side=leftHanded?-1:1;
            bodyAuth=bodyAuth.add(new Vec(8*strike,side*(-22*wind+32*strike),0));
            carryAuth=carryAuth.add(new Vec(-12*wind+8*strike,side*(-25*wind+48*strike),side*12*strike));
            carryPos=carryPos.add(new Vec(side*1.5*strike,0,2*wind-3*strike));
        }
        double progress=clamp(reloadProgress);var reload=com.codex.blacklight.firearms.FirearmReloadMotion.sample(progress);double release=reload.release();
        double lookYaw=Double.isFinite(yaw)?yaw:0,lookPitch=Double.isFinite(pitch)?pitch:0;
        double limit=PROFILE.number("relative_aim_limit_radians");
        double relativeYaw=Math.max(-limit,Math.min(limit,lookYaw)),relativePitch=Math.max(-limit,Math.min(limit,lookPitch));
        var torsoRotation=nativeDegrees(new double[]{bodyAuth.x,bodyAuth.y,bodyAuth.z})
                .add(new Vec(lookPitch-relativePitch,lookYaw-relativeYaw,0)).mul(weight);Matrix torso=rotation(torsoRotation);
        Vec bob=new Vec(rootAuth.x,-rootAuth.y,rootAuth.z).mul(weight),torsoPosition=bob.add(WAIST).sub(torso.point(WAIST));
        Vec carryRotation=nativeDegrees(new double[]{carryAuth.x,carryAuth.y,carryAuth.z});
        carryRotation=carryRotation.add(new Vec(relativePitch,relativeYaw,0)).add(new Vec(reload.lower()*.16,0,(leftHanded?-1:1)*reload.lower()*-.16));
        Matrix carry=rotation(carryRotation);Vec carryPosition=CARRY.add(new Vec(carryPos.x,-carryPos.y,carryPos.z).mul(weight));
        Reference mainReference=leftHanded?mirror(MAIN):MAIN,supportReference=leftHanded?mirror(SUPPORT):SUPPORT;
        carryPosition=fitCarry(carryPosition,carry,mainReference,supportReference);
        Vec mainTarget=carryPosition.add(carry.point(mainReference.wrist.sub(CARRY)));
        Vec supportTarget=carryPosition.add(carry.point(supportReference.wrist.sub(CARRY)));
        Vec magazine=vec(PROFILE.vector("magazine_grip")).add(new Vec(reload.bolt()*1.6,reload.handDown()*12,-reload.handBack()*4));
        if(leftHanded)magazine=magazine.mirror();
        supportTarget=mix(supportTarget,carryPosition.add(carry.point(magazine.sub(CARRY))),release);
        Reference posedMain=reach(mainReference,mainTarget),posedSupport=reach(supportReference,supportTarget);
        Arm right=poseArm(leftHanded?posedSupport:posedMain,torso,torsoPosition,weight);
        Arm left=poseArm(leftHanded?posedMain:posedSupport,torso,torsoPosition,weight);
        Vec headRotation=torso.mul(rotation(nativeDegrees(new double[]{headAuth.x,headAuth.y,headAuth.z}))).angles()
                .add(new Vec(relativePitch,relativeYaw,0));
        Vec rLeg=mix(mix(vec(PROFILE.sample("walk","Left Leg","rotation",walkClock)),vec(PROFILE.sample("run","Left Leg","rotation",runClock)),run).mul(move),vec(PROFILE.sample("crouch","Left Leg","rotation",crouchClock)),crouch);
        Vec lLeg=mix(mix(vec(PROFILE.sample("walk","Right Leg","rotation",walkClock)),vec(PROFILE.sample("run","Right Leg","rotation",runClock)),run).mul(move),vec(PROFILE.sample("crouch","Right Leg","rotation",crouchClock)),crouch);
        Vec rPos=vec(PROFILE.sample("crouch","Left Leg","position",crouchClock)).mul(crouch),lPos=vec(PROFILE.sample("crouch","Right Leg","position",crouchClock)).mul(crouch);
        return new Pose(right,left,torsoPosition,torsoRotation,carryPosition,carryRotation,torsoPosition,headRotation,
                new Vec(-1.9,12-rPos.y,rPos.z).add(bob),nativeDegrees(new double[]{rLeg.x,rLeg.y,rLeg.z}),
                new Vec(1.9,12-lPos.y,lPos.z).add(bob),nativeDegrees(new double[]{lLeg.x,lLeg.y,lLeg.z}),ZERO,ZERO,ZERO,ZERO,leftHanded,1-release,weight);
    }
    private static Arm poseArm(Reference reference,Matrix torso,Vec torsoPosition,double weight){
        return new Arm(torsoPosition.add(torso.point(reference.shoulder)),torso.mul(reference.upper).angles().mul(weight),reference.lower.angles().mul(weight));
    }
    /** Retarget original wrist/spine motion onto fixed native shoulders and one rigid firearm. */
    public static Pose original(Pose fallback,P1MilitaryMotion.Frame frame,double influence,double reloadProgress,double yaw,double pitch){
        return original(fallback,frame,influence,reloadProgress,yaw,pitch,influence);
    }
    public static Pose original(Pose fallback,P1MilitaryMotion.Frame frame,double influence,double reloadProgress,double yaw,double pitch,double legInfluence){
        return original(fallback,frame,influence,reloadProgress,yaw,pitch,legInfluence,0);
    }
    public static Pose original(Pose fallback,P1MilitaryMotion.Frame frame,double influence,double reloadProgress,double yaw,double pitch,double legInfluence,double supportRelease){
        return original(fallback,frame,influence,reloadProgress,yaw,pitch,legInfluence,supportRelease,null);
    }
    public static Pose radioOriginal(Pose base,P1MilitaryMotion motion,double progress,double weight,double yaw,double pitch){
        return original(base,motion.sample("radio",clamp(progress)*motion.frames("radio")),weight,0,yaw,pitch,0,1,motion.sample("radio",motion.frames("radio")*.5));
    }
    private static Pose original(Pose fallback,P1MilitaryMotion.Frame frame,double influence,double reloadProgress,double yaw,double pitch,double legInfluence,double supportRelease,P1MilitaryMotion.Frame gestureAnchor){
        if(frame==null||influence<=0)return fallback;
        boolean left=fallback.leftHanded;double weight=clamp(influence);
        Matrix torso=sourceDelta(frame,"Spine_3",left);
        Vec torsoPosition=WAIST.sub(torso.point(WAIST));
        Matrix aim=rotation(vec(PROFILE.vector("weapon_mount_rotation"))).transpose();if(left)aim=aim.mirror();
        Reference main=left?mirror(MAIN):MAIN,support=left?mirror(SUPPORT):SUPPORT;
        Vec neutral=fitCarry(CARRY.add(new Vec(0,-3.25,0)),aim,main,support);
        Vec anchor=neutral.add(aim.point(main.wrist.sub(CARRY)));
        Vec target=anchor.add(sourceHandDelta(frame,"Wrist_R",left));
        Matrix worldGun=sourceDelta(frame,"Wrist_R",left).mul(aim);
        // Aim remains relative to the NPC, while source root travel stays server-owned.
        Matrix look=rotation(new Vec(pitch,yaw,0));worldGun=look.mul(worldGun);target=look.point(target);
        Matrix carry=torso.transpose().mul(worldGun);
        Vec localTarget=torso.transpose().point(target.sub(torsoPosition));
        Vec carryPosition=localTarget.sub(carry.point(main.wrist.sub(CARRY)));
        carryPosition=fitCarry(carryPosition,carry,main,support);
        Vec supportTarget=carryPosition.add(carry.point(support.wrist.sub(CARRY)));
        double release=Math.max(com.codex.blacklight.firearms.FirearmReloadMotion.sample(clamp(reloadProgress)).release(),clamp(supportRelease));
        if(release>0){
            Vec supportAnchor=neutral.add(aim.point(support.wrist.sub(CARRY)));
            Vec moving;
            if(gestureAnchor!=null){
                // Bind the middle of the source call to this rig's earpiece, retaining its head-relative approach and withdrawal.
                var offset=new org.joml.Vector3d(frame.joint("Wrist_L").position()).sub(frame.joint("Head").position());
                var anchorOffset=new org.joml.Vector3d(gestureAnchor.joint("Wrist_L").position()).sub(gestureAnchor.joint("Head").position());
                new org.joml.Quaterniond(frame.joint("Head").rotation()).invert().transform(offset);
                new org.joml.Quaterniond(gestureAnchor.joint("Head").rotation()).invert().transform(anchorOffset);
                frame.rest("Head").rotation().transform(offset.sub(anchorOffset));
                Vec relative=new Vec(offset.x*(left?-20:20),-offset.y*20,offset.z*20).add(new Vec(left?-4.7:4.7,-3.5,-.4));
                moving=torsoPosition.add(look.point(sourceDelta(frame,"Head",left).point(relative)));
            }else if(supportRelease>0){
                // A radio/impact gesture is anchored to the head, not to a magazine grip below the chest.
                var offset=new org.joml.Vector3d(frame.joint("Wrist_L").position()).sub(frame.joint("Head").position());
                Vec nativeOffset=new Vec(offset.x*(left?-20:20),-offset.y*20,offset.z*20);
                moving=torsoPosition.add(look.point(sourceDelta(frame,"Head",left).point(new Vec(0,-4,0)).add(nativeOffset)));
            }else moving=look.point(supportAnchor.add(sourceHandDelta(frame,"Wrist_L",left)));
            supportTarget=mix(supportTarget,torso.transpose().point(moving.sub(torsoPosition)),release);
        }
        Vec head=look.mul(sourceDelta(frame,"Head",left)).angles();
        Vec torsoBlend=blendRotation(fallback.torsoRotation,torso.angles(),weight),positionBlend=mix(fallback.torsoPosition,torsoPosition,weight);
        Vec carryBlend=blendRotation(fallback.carryRotation,carry.angles(),weight);Matrix blendedTorso=rotation(torsoBlend),blendedCarry=rotation(carryBlend);
        Vec originBlend=fitCarry(mix(fallback.carryPosition,carryPosition,weight),blendedCarry,main,support);
        Vec finalMain=originBlend.add(blendedCarry.point(main.wrist.sub(CARRY)));
        Vec finalSupport=originBlend.add(blendedCarry.point(support.wrist.sub(CARRY)));
        if(release>0){
            var fallbackSupport=left?fallback.right:fallback.left;
            Vec from=wrist(fallbackSupport),to=torsoPosition.add(torso.point(supportTarget));
            finalSupport=blendedTorso.transpose().point(mix(from,to,weight).sub(positionBlend));
        }
        Reference mainPose=reach(main,finalMain),supportPose=reach(support,finalSupport);
        Arm right=poseArm(left?supportPose:mainPose,blendedTorso,positionBlend,1),leftArm=poseArm(left?mainPose:supportPose,blendedTorso,positionBlend,1);
        var upper=new Pose(right,leftArm,positionBlend,torsoBlend,originBlend,carryBlend,
                mix(fallback.headPosition,torsoPosition,weight),blendRotation(fallback.headRotation,head,weight),
                fallback.rightLegPosition,fallback.rightLegRotation,fallback.leftLegPosition,fallback.leftLegRotation,
                fallback.rightKneeRotation,fallback.leftKneeRotation,fallback.rightFootRotation,fallback.leftFootRotation,left,1-release,fallback.weight);
        return BlackwatchLegPose.original(upper,frame,legInfluence);
    }
    /** Authored no-private-data radio: keep the dominant hand on its real gun and bring the other hand to the earpiece. */
    public static Pose radio(Pose base,double influence){
        double w=clamp(influence);if(w==0)return base;boolean left=base.leftHanded;
        var torso=rotation(base.torsoRotation);var reference=left?mirror(SUPPORT):SUPPORT;var arm=left?base.right:base.left;
        Vec ear=base.headPosition.add(rotation(base.headRotation).point(new Vec(left?-4.7:4.7,-3.5,-.4)));
        Vec local=torso.transpose().point(mix(wrist(arm),ear,w).sub(base.torsoPosition));
        var support=poseArm(reach(reference,local),torso,base.torsoPosition,1);
        return new Pose(left?support:base.right,left?base.left:support,base.torsoPosition,base.torsoRotation,base.carryPosition,base.carryRotation,
                base.headPosition,base.headRotation,base.rightLegPosition,base.rightLegRotation,base.leftLegPosition,base.leftLegRotation,
                base.rightKneeRotation,base.leftKneeRotation,base.rightFootRotation,base.leftFootRotation,left,1-w,base.weight);
    }
    /** Standing damage never borrows the source hit clip: that clip finishes lying on the ground. */
    public static Pose standingHit(Pose base,double elapsedTicks,double aimWeight){
        if(!Double.isFinite(elapsedTicks)||elapsedTicks<=0||elapsedTicks>=20)return base;
        double envelope=smooth(elapsedTicks/3)*smooth((20-elapsedTicks)/8);
        return flinch(base,envelope*(1-clamp(aimWeight)));
    }
    /** Upper-body reaction about the actual waist leaves the legs and both rigid gun grips attached. */
    public static Pose flinch(Pose base,double influence){
        double w=clamp(influence);if(w==0)return base;Matrix change=rotation(new Vec(-.22*w,.06*w,.10*w));
        Vec pivot=base.torsoPosition.add(rotation(base.torsoRotation).point(WAIST));
        Arm right=new Arm(pivot.add(change.point(base.right.shoulder.sub(pivot))),change.mul(rotation(base.right.upper)).angles(),base.right.forearm);
        Arm left=new Arm(pivot.add(change.point(base.left.shoulder.sub(pivot))),change.mul(rotation(base.left.upper)).angles(),base.left.forearm);
        return new Pose(right,left,pivot.add(change.point(base.torsoPosition.sub(pivot))),change.mul(rotation(base.torsoRotation)).angles(),base.carryPosition,base.carryRotation,
                pivot.add(change.point(base.headPosition.sub(pivot))),change.mul(rotation(base.headRotation)).angles(),base.rightLegPosition,base.rightLegRotation,base.leftLegPosition,base.leftLegRotation,
                base.rightKneeRotation,base.leftKneeRotation,base.rightFootRotation,base.leftFootRotation,base.leftHanded,base.supportWeight,base.weight);
    }
    private static Vec sourceHandDelta(P1MilitaryMotion.Frame frame,String joint,boolean left){
        var current=new org.joml.Vector3d(frame.joint(joint).position()).sub(frame.joint("Neck").position());
        var reference=new org.joml.Vector3d(frame.rest(joint).position()).sub(frame.rest("Neck").position());current.sub(reference);
        // The source has approximately 0.6 m arms; the native rig has two 6-pixel segments.
        return new Vec(current.x*(left?-20:20),-current.y*20,current.z*20);
    }
    private static Matrix sourceDelta(P1MilitaryMotion.Frame frame,String joint,boolean left){
        var q=new org.joml.Quaterniond(frame.joint(joint).rotation()).mul(new org.joml.Quaterniond(frame.rest(joint).rotation()).invert());
        q.set(-q.x,q.y,-q.z,q.w);if(left)q.set(q.x,-q.y,-q.z,q.w);
        var m=new org.joml.Matrix3d().set(q);return new Matrix(new double[]{m.m00(),m.m10(),m.m20(),m.m01(),m.m11(),m.m21(),m.m02(),m.m12(),m.m22()});
    }
    private static Vec wrist(Arm arm){var upper=rotation(arm.upper);return arm.shoulder.add(upper.point(new Vec(0,6,0))).add(upper.mul(rotation(arm.forearm)).point(new Vec(0,6,0)));}
    private static Vec blendRotation(Vec a,Vec b,double t){
        var q=new org.joml.Quaterniond().rotationZYX(a.z,a.y,a.x).slerp(new org.joml.Quaterniond().rotationZYX(b.z,b.y,b.x),t);
        var m=new org.joml.Matrix3d().set(q);return new Matrix(new double[]{m.m00(),m.m10(),m.m20(),m.m01(),m.m11(),m.m21(),m.m02(),m.m12(),m.m22()}).angles();
    }
    private static double smooth(double x){double t=clamp(x);return t*t*(3-2*t);}
}
