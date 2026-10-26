package com.codex.blacklight.combat;

import net.minecraft.world.phys.Vec3;

/** Shared horizontal control only. Vertical acceleration, collision and impact
 * energy remain owned by BulletdivePhysics and the real supporting surface. */
public final class BulletdiveSteering {
    public static final double SPEED=1.6, ACCELERATION=.32, BRAKING=.48;
    private BulletdiveSteering() { }

    public static boolean retainAltitudeLock(Vec3 player, boolean grounded, Vec3 target, double range) {
        return !grounded && player.y-target.y>range*.5
                && Math.hypot(player.x-target.x,player.z-target.z)<=range;
    }

    /** Positive side input is the existing movement protocol's right key.
     * Held movement overrides guidance; no input brakes or follows an explicit lock. */
    public static Vec3 horizontal(Vec3 position,Vec3 velocity,float yaw,int forward,int right,Vec3 aim) {
        Vec3 current=finite(velocity)?new Vec3(velocity.x,0,velocity.z):Vec3.ZERO;
        int f=Integer.signum(forward),r=Integer.signum(right);
        boolean manual=f!=0||r!=0;
        Vec3 desired=Vec3.ZERO,offset=null;
        if(manual && Float.isFinite(yaw)) {
            double angle=Math.toRadians(yaw)+Math.atan2(r,f);
            desired=new Vec3(-Math.sin(angle)*SPEED,0,Math.cos(angle)*SPEED);
        } else if(finite(position)&&finite(aim)) {
            offset=new Vec3(aim.x-position.x,0,aim.z-position.z);
            double distance=offset.length();
            // Approach without oscillating across a stationary target. This is
            // physical lateral steering, never an impact-position teleport.
            if(distance>1e-6)desired=offset.scale(Math.min(SPEED,distance*.35)/distance);
        }
        Vec3 delta=desired.subtract(current);
        double rate=manual?ACCELERATION:BRAKING,length=delta.length();
        Vec3 next=current.add(length>rate?delta.scale(rate/length):delta);
        if(next.lengthSqr()>SPEED*SPEED)next=next.normalize().scale(SPEED);
        if(offset!=null && next.dot(offset)>0 && next.lengthSqr()>offset.lengthSqr())next=offset;
        return next;
    }
    private static boolean finite(Vec3 vector) {
        return vector!=null&&Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);
    }
}
