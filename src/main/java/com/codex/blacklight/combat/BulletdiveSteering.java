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
    /** Locked dive: solve the remaining fall time against the real dive speed curve and
     * spread the horizontal correction over it, leading a moving target, so the
     * crater lands on the target instead of drifting by the capped steering error. */
    public static final double GUIDED_MAX=14;
    public static Vec3 guided(Vec3 position,Vec3 target,Vec3 targetVelocity,int fallingTick,float yaw,int forward,int right){
        double drop=Math.max(0,position.y-target.y);int n=1;double travelled=0;
        while(n<4000){travelled+=BulletdivePhysics.downSpeed(fallingTick+n);if(travelled>=drop)break;n++;}
        // A horizontal step above two blocks per tick is a teleport or a stale previous position,
        // not motion; extrapolating it sends the crater far past the target.
        Vec3 step=finite(targetVelocity)?new Vec3(targetVelocity.x,0,targetVelocity.z):Vec3.ZERO;
        if(step.lengthSqr()>4)step=Vec3.ZERO;else if(step.lengthSqr()>1)step=step.normalize();
        Vec3 lead=step.scale(Math.min(n,40));
        Vec3 offset=new Vec3(target.x+lead.x-position.x,0,target.z+lead.z-position.z);
        Vec3 next=offset.scale(1D/n);
        int f=Integer.signum(forward),r=Integer.signum(right);
        if((f!=0||r!=0)&&Float.isFinite(yaw)){
            // Movement keys only nudge the guided line; they no longer cancel the lock.
            double angle=Math.toRadians(yaw)+Math.atan2(r,f);
            next=next.add(-Math.sin(angle)*.35,0,Math.cos(angle)*.35);
        }
        if(next.lengthSqr()>GUIDED_MAX*GUIDED_MAX)next=next.normalize().scale(GUIDED_MAX);
        return next;
    }
    private static boolean finite(Vec3 vector) {
        return vector!=null&&Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);
    }
}
