package com.codex.blacklight.military.aircraft;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Project flight dynamics. The source Blackhawk's BrakeDistance=20 informs the approach envelope. */
public final class HelicopterFlight {
    public static final double BRAKE_DISTANCE=20,MAX_SPEED=.65,ACCELERATION=.035;
    public static Vec3 velocity(Vec3 velocity,Vec3 position,Vec3 destination,double speed){
        if(!finite(velocity)||!finite(position)||!finite(destination)||!Double.isFinite(speed)||speed<=0)return Vec3.ZERO;
        Vec3 difference=destination.subtract(position);double distance=difference.length();
        if(distance<.025&&velocity.length()<.04)return difference;
        double desiredSpeed=Math.min(speed,Math.min(distance*.15,speed*distance/BRAKE_DISTANCE));
        Vec3 desired=difference.normalize().scale(desiredSpeed);
        Vec3 change=desired.subtract(velocity);if(change.length()>ACCELERATION)change=change.normalize().scale(ACCELERATION);
        return velocity.add(change);
    }
    public static boolean finite(Vec3 value){return value!=null&&Double.isFinite(value.x)&&Double.isFinite(value.y)&&Double.isFinite(value.z);}
    /** Keep lift available against a side wall, and horizontal motion on a floor.
     * Every candidate still passes the caller's full swept airframe check. */
    public static Vec3 slide(Vec3 step,java.util.function.Predicate<Vec3> clear){
        if(clear.test(step))return step;
        Vec3 best=clip(step,clear);
        for(Vec3 candidate:new Vec3[]{new Vec3(0,step.y,0),new Vec3(step.x,0,step.z)}){
            if(candidate.lengthSqr()<1e-12)continue;
            Vec3 safe=clear.test(candidate)?candidate:clip(candidate,clear);
            if(safe.lengthSqr()>best.lengthSqr())best=safe;
        }
        return best;
    }
    private static Vec3 clip(Vec3 step,java.util.function.Predicate<Vec3> clear){
        double low=0,high=1;
        for(int i=0;i<12;i++){double middle=(low+high)/2;
            if(clear.test(step.scale(middle)))low=middle;else high=middle;}
        return step.scale(low);
    }
    public static float heading(Vec3 movement,float previous){
        if(movement.horizontalDistanceSqr()<.001)return previous;
        float desired=(float)Math.toDegrees(Math.atan2(movement.x,movement.z));
        return previous+Mth.clamp(Mth.wrapDegrees(desired-previous),-3,3);
    }
    private HelicopterFlight(){}
}
