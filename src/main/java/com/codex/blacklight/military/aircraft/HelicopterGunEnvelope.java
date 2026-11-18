package com.codex.blacklight.military.aircraft;

import net.minecraft.world.phys.Vec3;

/** engage_apache gun branches 127 and 151, before the separate muzzle/cover gate.
 * Native actor-space distances use the live MC yaw/bank basis at one block per
 * source unit, matching this port's navigation adapter. They are not gun range.
 */
final class HelicopterGunEnvelope {
    enum Profile { NONE, ORDINARY_127, STRAFE_151 }

    private HelicopterGunEnvelope() {}

    static boolean permits(Profile profile,Vec3 actor,Vec3 target,float yaw,float bank,boolean helicopter){
        if(profile==null||profile==Profile.NONE||!finite(actor)||!finite(target)||!Float.isFinite(yaw)||!Float.isFinite(bank))return false;
        Vec3 delta=target.subtract(actor);
        Vec3 right=HelicopterArmament.rotate(new Vec3(1,0,0),yaw,bank);
        Vec3 up=HelicopterArmament.rotate(new Vec3(0,1,0),yaw,bank);
        Vec3 forward=HelicopterArmament.rotate(new Vec3(0,0,1),yaw,bank);
        // 1011e650 -> 107bc530 dots the actor-to-target translation with the
        // actor matrix rows before selecting XZ, Y or AbsY. Source forward is
        // -rowZ; our authored aircraft faces +Z (XZ length is sign-independent).
        double localY=delta.dot(up),distance=Math.hypot(delta.dot(right),delta.dot(forward));
        double yawAngle=angle(normalize(forward.multiply(1,0,1)),normalize(delta.multiply(1,0,1)));
        double pitchAngle=pitch(forward,delta);
        if(profile==Profile.ORDINARY_127)return distance>1&&distance<=60&&Math.abs(localY)<=30&&localY<0
                &&yawAngle<=Math.toRadians(60)&&pitchAngle<=Math.toRadians(helicopter?40:30);
        return distance>10&&distance<=100&&yawAngle<=Math.toRadians(25)&&pitchAngle<Math.toRadians(45);
    }

    /** 101e5f80..101e6021: normalize both vectors, replace the target's XZ
     * with forward.XZ, normalize again, then acos. This deliberately is not
     * atan2(deltaY, horizontalDistance): for horizontal forward it is
     * atan(abs(deltaY) / delta.length()). Signed=false in both gun branches.
     */
    static double pitch(Vec3 forward,Vec3 delta){
        if(!finite(forward)||!finite(delta))return Double.NaN;
        Vec3 f=normalize(forward),d=normalize(delta);
        return angle(f,normalize(new Vec3(f.x,d.y,f.z)));
    }

    private static Vec3 normalize(Vec3 vector){
        double squared=vector.lengthSqr();
        return squared<9.999999439624929E-11?Vec3.ZERO:vector.scale(1/Math.sqrt(squared));
    }
    private static double angle(Vec3 a,Vec3 b){return Math.acos(Math.max(-1,Math.min(1,a.dot(b))));}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
