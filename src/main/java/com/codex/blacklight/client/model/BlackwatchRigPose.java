package com.codex.blacklight.client.model;

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
        double weight=clamp(equippedWeight),move=clamp(movement*2),run=clamp((movement-.55)/.3),crouch=clamp(crouchWeight);
        Vec idleCarry=vec(PROFILE.sample("idle","Two Hand Carry","rotation",seconds));
        Vec carryAuth=mix(idleCarry,mix(vec(PROFILE.sample("walk","Two Hand Carry","rotation",seconds)),vec(PROFILE.sample("run","Two Hand Carry","rotation",seconds)),run),move);
        Vec bodyAuth=mix(ZERO,mix(vec(PROFILE.sample("walk","Upper Body","rotation",seconds)),vec(PROFILE.sample("run","Upper Body","rotation",seconds)),run),move);
        Vec rootAuth=mix(ZERO,mix(vec(PROFILE.sample("walk","Blackwatch Rig","position",seconds)),vec(PROFILE.sample("run","Blackwatch Rig","position",seconds)),run),move);
        Vec carryPos=vec(PROFILE.sample("idle","Two Hand Carry","position",seconds)).mul(1-move);
        Vec headAuth=vec(PROFILE.sample("idle","Head","rotation",seconds)).mul(1-move).add(vec(PROFILE.sample("run","Head","rotation",seconds)).mul(move*run));
        double crouchClock=movement>.04?seconds:0;
        bodyAuth=mix(bodyAuth,vec(PROFILE.sample("crouch","Upper Body","rotation",crouchClock)),crouch);
        carryAuth=mix(carryAuth,vec(PROFILE.sample("crouch","Two Hand Carry","rotation",crouchClock)),crouch);
        rootAuth=mix(rootAuth,vec(PROFILE.sample("crouch","Blackwatch Rig","position",crouchClock)),crouch);
        headAuth=mix(headAuth,vec(PROFILE.sample("crouch","Head","rotation",crouchClock)),crouch);
        if(Double.isFinite(shotAge)&&shotAge>=0&&shotAge<=.5){
            String shot=crouch>.5?"shoot_crouching":"shoot_standing";
            carryAuth=carryAuth.add(vec(PROFILE.sample(shot,"Two Hand Carry","rotation",shotAge)).sub(vec(PROFILE.sample(shot,"Two Hand Carry","rotation",0))));
            carryPos=carryPos.add(vec(PROFILE.sample(shot,"Two Hand Carry","position",shotAge)).sub(vec(PROFILE.sample(shot,"Two Hand Carry","position",0))));
        }
        double progress=clamp(reloadProgress),release=Math.pow(Math.sin(Math.PI*progress),2);
        double lookYaw=Double.isFinite(yaw)?yaw:0,lookPitch=Double.isFinite(pitch)?pitch:0;
        double limit=PROFILE.number("relative_aim_limit_radians");
        double relativeYaw=Math.max(-limit,Math.min(limit,lookYaw)),relativePitch=Math.max(-limit,Math.min(limit,lookPitch));
        var torsoRotation=nativeDegrees(new double[]{bodyAuth.x,bodyAuth.y,bodyAuth.z})
                .add(new Vec(lookPitch-relativePitch,lookYaw-relativeYaw,0)).mul(weight);Matrix torso=rotation(torsoRotation);
        Vec bob=new Vec(rootAuth.x,-rootAuth.y,rootAuth.z).mul(weight),torsoPosition=bob.add(WAIST).sub(torso.point(WAIST));
        Vec carryRotation=nativeDegrees(new double[]{carryAuth.x,carryAuth.y,carryAuth.z});
        carryRotation=carryRotation.add(new Vec(relativePitch,relativeYaw,0)).add(new Vec(release*.12,0,(leftHanded?-1:1)*release*.08));
        Matrix carry=rotation(carryRotation);Vec carryPosition=CARRY.add(new Vec(carryPos.x,-carryPos.y,carryPos.z).mul(weight));
        Reference mainReference=leftHanded?mirror(MAIN):MAIN,supportReference=leftHanded?mirror(SUPPORT):SUPPORT;
        carryPosition=fitCarry(carryPosition,carry,mainReference,supportReference);
        Vec mainTarget=carryPosition.add(carry.point(mainReference.wrist.sub(CARRY)));
        Vec supportTarget=carryPosition.add(carry.point(supportReference.wrist.sub(CARRY)));
        Vec magazine=vec(PROFILE.vector("magazine_grip")).add(new Vec(0,Math.sin(progress*Math.PI*6)*release*.2,0));
        if(leftHanded)magazine=magazine.mirror();
        supportTarget=mix(supportTarget,carryPosition.add(carry.point(magazine.sub(CARRY))),release);
        Reference posedMain=reach(mainReference,mainTarget),posedSupport=reach(supportReference,supportTarget);
        Arm right=poseArm(leftHanded?posedSupport:posedMain,torso,torsoPosition,weight);
        Arm left=poseArm(leftHanded?posedMain:posedSupport,torso,torsoPosition,weight);
        Vec headRotation=torso.mul(rotation(nativeDegrees(new double[]{headAuth.x,headAuth.y,headAuth.z}))).angles()
                .add(new Vec(relativePitch,relativeYaw,0));
        Vec rLeg=mix(mix(vec(PROFILE.sample("walk","Left Leg","rotation",seconds)),vec(PROFILE.sample("run","Left Leg","rotation",seconds)),run).mul(move),vec(PROFILE.sample("crouch","Left Leg","rotation",crouchClock)),crouch);
        Vec lLeg=mix(mix(vec(PROFILE.sample("walk","Right Leg","rotation",seconds)),vec(PROFILE.sample("run","Right Leg","rotation",seconds)),run).mul(move),vec(PROFILE.sample("crouch","Right Leg","rotation",crouchClock)),crouch);
        Vec rPos=vec(PROFILE.sample("crouch","Left Leg","position",crouchClock)).mul(crouch),lPos=vec(PROFILE.sample("crouch","Right Leg","position",crouchClock)).mul(crouch);
        return new Pose(right,left,torsoPosition,torsoRotation,carryPosition,carryRotation,torsoPosition,headRotation,
                new Vec(-1.9,12-rPos.y,rPos.z).add(bob),nativeDegrees(new double[]{rLeg.x,rLeg.y,rLeg.z}),
                new Vec(1.9,12-lPos.y,lPos.z).add(bob),nativeDegrees(new double[]{lLeg.x,lLeg.y,lLeg.z}),leftHanded,1-release,weight);
    }
    private static Arm poseArm(Reference reference,Matrix torso,Vec torsoPosition,double weight){
        return new Arm(torsoPosition.add(torso.point(reference.shoulder)),torso.mul(reference.upper).angles().mul(weight),reference.lower.angles().mul(weight));
    }
}
