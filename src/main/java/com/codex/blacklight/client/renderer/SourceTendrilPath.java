package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;

/** The P1 base path through two moving joints, before wave and mesh deformation. */
final class SourceTendrilPath {
    private static final double EPSILON=1.0e-5;
    private static final double TURN=2*Math.PI;

    private SourceTendrilPath() {}

    /**
     * The launch direction is relative to the root-to-tip vector and world up.
     * Radius, root and tip must be supplied in the same world units.
     */
    static Vec3[] sample(Vec3 root,Vec3 tip,double radius,double rollDegrees,double pitchDegrees,int sections){
        return sample(root,tip,radius,rollDegrees,pitchDegrees,sections,Double.POSITIVE_INFINITY);
    }

    /** Paired P1 poses map to Minecraft world as (x, y, -z) at zero yaw. Sample in the
     * native handedness, including its alternate frame for nearly vertical targets. */
    static Vec3[] sampleMinecraft(Vec3 root,Vec3 tip,double radius,double rollDegrees,double pitchDegrees,int sections,double travelled){
        Vec3[] path=sample(reflectZ(root),reflectZ(tip),radius,rollDegrees,pitchDegrees,sections,travelled);
        for(int i=0;i<path.length;i++)path[i]=reflectZ(path[i]);
        return path;
    }
    private static Vec3 reflectZ(Vec3 p){return new Vec3(p.x,p.y,-p.z);}

    /** Advance the head by the given source speed times elapsed time, expressed in world units. */
    static Vec3[] sample(Vec3 root,Vec3 tip,double radius,double rollDegrees,double pitchDegrees,int sections,double travelled){
        if(sections<1)throw new IllegalArgumentException("sections must be positive");
        var result=new Vec3[sections+1];
        Vec3 difference=tip.subtract(root);
        double distance=difference.length();
        double pitch=Math.toRadians(pitchDegrees),forwardLaunch=Math.cos(pitch);
        if(distance<EPSILON||radius<=0||1-forwardLaunch<EPSILON||Math.abs(Math.sin(pitch))<EPSILON){
            return straight(result,root,tip,distance,travelled);
        }

        Vec3 forward=difference.scale(1/distance);
        Vec3 right=new Vec3(forward.z,0,-forward.x);
        if(right.lengthSqr()<EPSILON)right=new Vec3(0,-forward.z,forward.y);
        right=right.normalize();
        Vec3 up=forward.cross(right);
        double roll=Math.toRadians(rollDegrees);
        Vec3 lateral=right.scale(Math.sin(roll)).subtract(up.scale(Math.cos(roll)));
        if(Math.sin(pitch)<0)lateral=lateral.scale(-1);

        double sideLaunch=Math.abs(Math.sin(pitch));
        double centerSide=0,centerForward=0,goalDistance=0;
        double fitted=radius;
        // The native builder tries at most five times to fit the launch circle to a close target.
        for(int attempt=0;attempt<5;attempt++){
            centerSide=-forwardLaunch*fitted;
            centerForward=sideLaunch*fitted;
            goalDistance=Math.hypot(centerSide,distance-centerForward);
            if(fitted<=goalDistance)break;
            fitted=goalDistance;
        }
        if(fitted<EPSILON||goalDistance<EPSILON){
            return straight(result,root,tip,distance,travelled);
        }

        double cosine=clamp(fitted/goalDistance);
        double sine=Math.sqrt(Math.max(0,1-cosine*cosine));
        double dx=-centerSide/goalDistance,dz=(distance-centerForward)/goalDistance;
        double exitSide=centerSide+fitted*(cosine*dx+sine*dz);
        double exitForward=centerForward+fitted*(cosine*dz-sine*dx);
        double startSign=Math.abs(centerSide)>EPSILON?Math.signum(-centerSide):Math.signum(-centerForward);
        double startAngle=startSign*Math.acos(clamp(-centerForward/fitted));
        // The source reads the exit Z through acos here; using atan2 changes short, back-facing tracks.
        double exitAngle=Math.acos(clamp((exitForward-centerForward)/fitted));
        double sweep=(startAngle-exitAngle)%TURN;
        if(sweep<0)sweep+=TURN;
        double arcLength=sweep*fitted;
        double straightLength=Math.hypot(exitSide,distance-exitForward);
        double length=arcLength+straightLength;
        double centerLength=Math.hypot(centerSide,centerForward);
        double exitRadialSide=(exitSide-centerSide)/fitted,exitRadialForward=(exitForward-centerForward)/fitted;
        double angularExtent=Math.acos(clamp(-(centerSide*exitRadialSide+centerForward*exitRadialForward)/centerLength));
        if(sweep>Math.PI)angularExtent=TURN-angularExtent;
        double segmentSpacing=(angularExtent*fitted+straightLength)/sections;
        double head=Math.min(length,travelled);
        result[0]=root;
        for(int i=1;i<=sections;i++){
            double at=head-segmentSpacing*(sections-i);
            if(at<EPSILON){result[i]=root;continue;}
            if(at>=length){result[i]=tip;continue;}
            double side,z;
            if(at<arcLength){
                double angle=startAngle-at/fitted;
                side=centerSide+fitted*Math.sin(angle);
                z=centerForward+fitted*Math.cos(angle);
            }else{
                double u=straightLength>EPSILON?Math.min(1,(at-arcLength)/straightLength):1;
                side=exitSide*(1-u);
                z=exitForward+(distance-exitForward)*u;
            }
            result[i]=root.add(lateral.scale(side)).add(forward.scale(z));
        }
        return result;
    }

    private static Vec3[] straight(Vec3[] result,Vec3 root,Vec3 tip,double distance,double travelled){
        double head=Math.min(distance,travelled),spacing=distance/(result.length-1);
        result[0]=root;
        for(int i=1;i<result.length;i++){
            double at=head-spacing*(result.length-1-i);
            result[i]=at<EPSILON?root:root.lerp(tip,Math.min(1,at/distance));
        }
        return result;
    }

    private static double clamp(double value){return Math.max(-1,Math.min(1,value));}
}
