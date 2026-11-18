package com.codex.blacklight.infection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** P1 infected2 jump_attack: source timing/ballistics, Minecraft contact/length scale.
 * FIG offsets: windup 12648, aim 12776, JumpTo 13028, probes 13816/14112,
 * conditional punch 14488/14616, landing 15108/15300. */
public final class CommonInfectedLeap {
    public static final int HAND=3, AIM_TICKS=5, LAUNCH_TICK=7, PROBE_DELAY=5;
    public static final int PUNCH_BEGIN=4, PUNCH_END=11, LAND_TICKS=10, TIMEOUT=48, COOLDOWN=80;
    public static final double MIN_RANGE=3, MAX_RANGE=5;
    // FIG JumpTo@13028; original setters convert degrees to radians. One MC tick is .05 seconds.
    public static final double TRACK_DEGREES_PER_SECOND=50, MIN_TRACK_VERTICAL_SPEED=-100/20D;
    public static final double GRAVITY=30, MIN_INITIAL_SPEED=5, MAX_INITIAL_SPEED=17, LAUNCH_DEGREES=57;
    // FIG13028 -> choreo root-motion driver, NOT PuppetAnimation/bone blending.
    public static final double ROOT_BLEND_OUT_SECONDS=.33333298563957214;
    private static final double TICKS_PER_SECOND=20, HALF_PI=Math.PI/2;
    public record Tracking(Vec3 velocity,boolean active){}
    /** Native1093b4ed..b50b subtracts dt/blendOut before evaluating the driver.
     * MC keeps vertical gravity/contact under ordinary living travel on exit. */
    public static final class RootExit {
        private long started=-1;
        private Vec3 velocity=Vec3.ZERO;
        public void begin(long now,Vec3 lastVelocity){
            if(weight(now)>0)return;
            clear();
            if(!finite(lastVelocity)||lastVelocity.horizontalDistanceSqr()<1e-12)return;
            started=now;velocity=new Vec3(lastVelocity.x,0,lastVelocity.z);
        }
        public void clear(){started=-1;velocity=Vec3.ZERO;}
        public double weight(long now){
            if(started<0||now<started)return 0;
            return Math.max(0,1-(now-started+1)/(ROOT_BLEND_OUT_SECONDS*TICKS_PER_SECOND));
        }
        public Vec3 blend(Vec3 ordinary,long now){
            double weight=weight(now);
            if(weight==0){clear();return ordinary;}
            return new Vec3(ordinary.x+(velocity.x-ordinary.x)*weight,ordinary.y,
                    ordinary.z+(velocity.z-ordinary.z)*weight);
        }
    }
    private CommonInfectedLeap(){}
    public static boolean range(double horizontal,double vertical){
        return Double.isFinite(horizontal)&&Double.isFinite(vertical)&&horizontal>=MIN_RANGE&&horizontal<=MAX_RANGE&&Math.abs(vertical)<=.8;
    }
    /** Horizontal stationary-target convenience; velocities are always blocks/tick. */
    public static Vec3 launch(float yaw,double distance){
        return launch(Vec3.directionFromRotation(0,yaw).scale(distance));
    }
    /** P1 100e17a0 / 10125c60. One native distance unit maps to one block.
     * The minimum speed applies ONLY to the near-target branch, not every jump. */
    public static Vec3 launch(Vec3 displacement){
        if(!finite(displacement)||displacement.lengthSqr()<1e-10)return Vec3.ZERO;
        double horizontal=displacement.horizontalDistance(),height=displacement.y;
        double angle=Math.max(-HALF_PI+.01,Math.min(HALF_PI-.01,launchAngle(displacement)));
        double cos=Math.cos(angle),sin=Math.sin(angle),speed;
        if(horizontal<1&&Math.abs(height)<1)speed=MIN_INITIAL_SPEED;
        else{
            double denominator=2*(cos*sin*horizontal-cos*cos*height);
            speed=denominator>0?Math.min(MAX_INITIAL_SPEED,Math.sqrt(GRAVITY*horizontal*horizontal/denominator)):MAX_INITIAL_SPEED;
        }
        // A vertical/zero vector has no native perpendicular rotation axis. Use a
        // finite vertical fallback; normal AI entry always has horizontal range >= 3.
        if(horizontal<1e-8)return new Vec3(0,Math.copySign(speed/TICKS_PER_SECOND,height),0);
        return new Vec3(displacement.x/horizontal*cos,sin,displacement.z/horizontal*cos).scale(speed/TICKS_PER_SECOND);
    }
    /** P1 100e2510 -> 100e1b60 -> 100e0ef0. Target velocity and signed vertical
     * acceleration use MC tick units; the solver works in native seconds. */
    public static Vec3 launch(Vec3 displacement,Vec3 targetVelocity,double targetAcceleration){
        if(!finite(displacement)||displacement.lengthSqr()<1e-10)return Vec3.ZERO;
        if(!finite(targetVelocity)||targetVelocity.lengthSqr()<1e-12||!Double.isFinite(targetAcceleration))return launch(displacement);
        var motion=targetVelocity.scale(TICKS_PER_SECOND);
        double targetGravity=Math.max(-50,Math.min(50,targetAcceleration*TICKS_PER_SECOND*TICKS_PER_SECOND));
        double angle=launchAngle(displacement),step=.1,speed=MAX_INITIAL_SPEED;int previousDirection=0;
        var horizontal=new Vec3(displacement.x,0,displacement.z);var horizontalMotion=new Vec3(motion.x,0,motion.z);
        for(int attempt=0;attempt<20;attempt++){
            double time=intercept(horizontal,horizontalMotion,speed*Math.cos(angle));
            Vec3 velocity=time>0?displacement.scale(1/time).add(motion).add(0,.5*time*(targetGravity+GRAVITY),0):null;
            if(time<0||velocity==null||!finite(velocity)){
                if(previousDirection==2)step*=.7;
                angle-=step;previousDirection=1;if(angle<0)break;
            }else if(time<.1){
                speed-=2;if(speed<MIN_INITIAL_SPEED)break;
            }else if(Math.atan2(velocity.y,velocity.horizontalDistance())<0){
                if(previousDirection==2)step*=.7;
                angle-=step;previousDirection=1;if(angle<0)break;
            }else if(velocity.length()>speed){
                if(previousDirection==1)step*=.7;
                angle+=step;previousDirection=2;if(angle>HALF_PI)break;
            }else return velocity.scale(1/TICKS_PER_SECOND);
        }
        // Native dynamic-solver failure returns to the stationary launch branch.
        return launch(displacement);
    }
    private static double launchAngle(Vec3 displacement){
        double elevation=Math.asin(Math.max(-1,Math.min(1,displacement.y/displacement.length())));
        double base=Math.toRadians(LAUNCH_DEGREES);
        return elevation>0?base+(HALF_PI-base)*elevation/HALF_PI:base+base*elevation/HALF_PI;
    }
    private static boolean finite(Vec3 value){return value!=null&&Double.isFinite(value.lengthSqr());}
    /** Positive root used by native 101262f0; a failed intercept has no solution. */
    private static double intercept(Vec3 displacement,Vec3 motion,double speed){
        double a=motion.lengthSqr()-speed*speed,b=2*displacement.dot(motion),c=displacement.lengthSqr();
        double discriminant=b*b-4*a*c;
        if(!(discriminant>0))return -1;
        double time=2*c/(Math.sqrt(discriminant)-b);
        return time>0&&Double.isFinite(time)?time:-1;
    }
    /** 100e2b60 tracks first, then subtracts gravity*dt; 109410c0 multiplies
     * that resulting velocity by dt. No vanilla drag or second gravity step. */
    public static Tracking advance(Vec3 velocity,Vec3 displacement,Vec3 targetVelocity,boolean active){
        var tracked=track(velocity,displacement,targetVelocity,active);
        return new Tracking(gravityStep(tracked.velocity()),tracked.active());
    }
    public static Vec3 gravityStep(Vec3 velocity){
        return finite(velocity)?velocity.add(0,-GRAVITY/(TICKS_PER_SECOND*TICKS_PER_SECOND),0):Vec3.ZERO;
    }
    /** P1 JumpToAction 100e22d0 -> 101265b0: lead a moving target, turn the
     * velocity direction at a bounded angular rate, and latch tracking off when
     * that direction is behind the projectile. Speed and source gravity stay separate. */
    public static Tracking track(Vec3 velocity,Vec3 displacement,Vec3 targetVelocity,boolean active){
        if(!Double.isFinite(velocity.lengthSqr()))return new Tracking(Vec3.ZERO,false);
        if(!active||velocity.y<MIN_TRACK_VERTICAL_SPEED)return new Tracking(velocity,false);
        double speed=velocity.length();
        if(speed<1e-8||!Double.isFinite(displacement.lengthSqr()))return new Tracking(velocity,false);
        if(!Double.isFinite(targetVelocity.lengthSqr()))targetVelocity=Vec3.ZERO;
        // Same positive intercept root used by 101262f0, in consistent blocks/tick units.
        double time=intercept(displacement,targetVelocity,speed);
        if(time>0)displacement=displacement.add(targetVelocity.scale(time));
        if(displacement.lengthSqr()<1e-12)return new Tracking(velocity,true);
        var from=velocity.scale(1/speed);var to=displacement.normalize();
        double dot=Math.max(-1,Math.min(1,from.dot(to)));
        if(dot<=0)return new Tracking(velocity,false);
        double angle=Math.acos(dot),step=Math.toRadians(TRACK_DEGREES_PER_SECOND)/20;
        if(angle<=step)return new Tracking(to.scale(speed),true);
        var tangent=to.subtract(from.scale(dot)).normalize();
        return new Tracking(from.scale(Math.cos(step)).add(tangent.scale(Math.sin(step))).scale(speed),true);
    }
    public static List<Vec3> clearance(Vec3 velocity){return clearance(velocity,0);}
    /** Untracked, bounded preview using the same gravity-before-move order. It
     * stops at descending landing elevation; the floor itself must not reject a
     * valid arc. Actual turning and block collision are checked each flight tick. */
    public static List<Vec3> clearance(Vec3 velocity,double landingHeight){
        if(!finite(velocity)||!Double.isFinite(landingHeight))return List.of();
        var path=new ArrayList<Vec3>();var at=Vec3.ZERO;
        for(int i=0;i<32;i++){
            velocity=gravityStep(velocity);var next=at.add(velocity);
            if(velocity.y<0&&next.y<=landingHeight){
                if(at.y>landingHeight){
                    var landing=at.lerp(next,(at.y-landingHeight)/(at.y-next.y));
                    path.add(new Vec3(landing.x,landingHeight,landing.z));
                }
                break;
            }
            path.add(next);at=next;
        }
        return List.copyOf(path);
    }
    /** Two original zero-damage root probes only open the punch branch. */
    public static boolean approaches(Vec3 feet,float yaw,AABB target){
        var forward=Vec3.directionFromRotation(0,yaw);
        for(double reach:new double[]{1.5,.75}){
            var center=feet.add(0,.75,0).add(forward.scale(reach));
            if(new AABB(center,center).inflate(.4).intersects(target))return true;
        }
        return false;
    }
    public static boolean damageWindow(float punchAge){return punchAge>=PUNCH_BEGIN&&punchAge<=PUNCH_END;}
    /** Same four-unit upper-arm pivot and six-unit forearm as CommonInfectedModel. */
    public static Vec3 wrist(Vec3 feet,float yaw,float leapAge,float punchAge){
        var p=CommonInfectedMotion.leap(leapAge,punchAge,-1);
        double torso=p.torso(),arm=p.armR()+torso,elbow=p.elbowR(),roll=.06+p.roll();
        double y=4+6*Math.cos(elbow),z=6*Math.sin(elbow);
        double ry=y*Math.cos(arm)-z*Math.sin(arm),rz=y*Math.sin(arm)+z*Math.cos(arm);
        double rx=-Math.cos(roll)-ry*Math.sin(roll);
        ry=-Math.sin(roll)+ry*Math.cos(roll);
        double modelX=-5+rx,modelY=12-10*Math.cos(torso)+p.bob()+ry,modelZ=-10*Math.sin(torso)+rz;
        return feet.add(new Vec3(modelX/16,1.501-modelY/16,-modelZ/16).yRot((float)Math.toRadians(-yaw)));
    }
    public static boolean touches(Vec3 before,Vec3 now,float yaw,float leapAge,float punchAge,AABB target){
        return touches(before,now,yaw,yaw,leapAge,punchAge,target);
    }
    /** Match the renderer's shortest body-yaw interpolation while the flight turns. */
    public static boolean touches(Vec3 before,Vec3 now,float previousYaw,float yaw,float leapAge,float punchAge,AABB target){
        if(!damageWindow(punchAge))return false;
        var box=target.inflate(.25);Vec3 previous=null;
        for(int i=0;i<=8;i++){
            float fraction=i/8F,age=punchAge-1+fraction;if(age<PUNCH_BEGIN)continue;
            var point=wrist(before.lerp(now,fraction),net.minecraft.util.Mth.rotLerp(fraction,previousYaw,yaw),leapAge-1+fraction,age);
            if(box.contains(point)||previous!=null&&box.clip(previous,point).isPresent())return true;
            previous=point;
        }
        return false;
    }
}
