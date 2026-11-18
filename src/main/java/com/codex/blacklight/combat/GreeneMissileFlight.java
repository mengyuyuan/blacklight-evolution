package com.codex.blacklight.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** P1 motherVomit MissileBehaviour, independent of entity/collision integration.
 * Source spatial units map 1:1 to blocks; all velocity/acceleration and time
 * arguments use seconds, never ticks. See greene-missile-flight-source.json. */
public final class GreeneMissileFlight {
    private static final double SOURCE_MOVEMENT_EPSILON=9.999999747378752e-6;
    public static final double RANGE=175,FRICTION_GAIN=50,BRAKE_DONE_SPEED_SQUARED=9.999999747378752e-6;
    /** Source user data, not a claim that the still-unresolved accuracy sampler is implemented. */
    public enum User {
        ANTI_AIR("motherVomit_AntiAir_user_ai",-10,10,-50,50,.30000001192092896,1,2,true,true,true),
        DEATH_FLOWER("motherVomit_DeathFlower_user_ai",2,4,-1.1399999856948853,1.1399999856948853,.5,0,0,true,false,false),
        INSECT_SWARM("motherVomit_InsectSwarm_user_ai",8,16,-1.1399999856948853,1.1399999856948853,.5,0,0,true,false,false),
        SHOTGUN("motherVomit_ShotgunBlast_user_ai",1,4,-1.1399999856948853,1.1399999856948853,.30000001192092896,0,0,false,false,true),
        RIFLE("motherVomit_user_ai",2,4,-1.1399999856948853,1.1399999856948853,.5,0,0,true,false,false);
        public final String sourceName;
        public final double accuracyMin,accuracyMax,accuracyAngleMin,accuracyAngleMax,accuracyGainSeconds,nonPlayerAccuracyScalar;
        public final int lookAt;
        public final boolean deadReckoning,groundAccuracy,randomAccuracy;
        User(String name,double min,double max,double angleMin,double angleMax,double gain,double nonPlayer,int lookAt,boolean lead,boolean ground,boolean random){
            sourceName=name;accuracyMin=min;accuracyMax=max;accuracyAngleMin=angleMin;accuracyAngleMax=angleMax;
            accuracyGainSeconds=gain;nonPlayerAccuracyScalar=nonPlayer;this.lookAt=lookAt;deadReckoning=lead;groundAccuracy=ground;randomAccuracy=random;
        }
        public static User fromSourceName(String name){for(var p:values())if(p.sourceName.equals(name))return p;return null;}
    }
    public enum Ammo {
        DEATH_FLOWER("motherVomit_DeathFlower_ammo",12,40,4,4,15,.5,true,false,1,0,0,.20000000298023224,35,20,200,35,1,100,"",User.DEATH_FLOWER),
        INSECT_SWARM("motherVomit_InsectSwarm_ammo",5,10,-1,3,5,10,true,true,1,1,.75,6,35,20,60,25,0,56,"",User.INSECT_SWARM),
        RIFLE("motherVomit_ammo",10,60,-1,-1,6,1,false,false,0,1,.5,10,35,20,100,25,.5,56,"motherVomit_Spin",User.RIFLE),
        SHOTGUN("motherVomit_ShotgunBlast_ammo",40,40,-1,-1,6,1,false,false,0,2,0,150,3,20,50,50,.20000000298023224,56,"motherVomit_Spin",User.SHOTGUN),
        ANTI_TANK("motherVomit_AntiTank_ammo",60,80,-1,-1,6,1,false,false,0,0,2,5,3,20,400,400,0,2500,"motherVomit_Spin",User.ANTI_AIR),
        ANTI_AIR("motherVomit_AntiAir_ammo",60,80,-1,-1,6,1,false,false,0,0,2,5,3,11,100,1000,0,5000,"motherVomit_Spin",User.ANTI_AIR);
        public final String sourceName,spin;
        public final double initialSpeed,speed,initialDeceleration,acceleration,lifetime,turnRate,trackingDelay,proximity,thickness,fireRate;
        public final int damageType,attackType;
        public final double damageOnPlayer,damageOnNpc,friendlyFire,impulse;
        public final boolean tracking,perfectTracking;
        public final User sourceUser;
        Ammo(String name,double initial,double speed,double deceleration,double acceleration,double life,double turn,boolean tracking,boolean perfect,
             double delay,double proximity,double thickness,double rate,int damageType,int attackType,double player,double npc,double friendly,double impulse,String spin,User user){
            sourceName=name;initialSpeed=initial;this.speed=speed;initialDeceleration=deceleration;this.acceleration=acceleration;lifetime=life;turnRate=turn;
            this.tracking=tracking;perfectTracking=perfect;trackingDelay=delay;this.proximity=proximity;this.thickness=thickness;fireRate=rate;
            this.damageType=damageType;this.attackType=attackType;damageOnPlayer=player;damageOnNpc=npc;friendlyFire=friendly;this.impulse=impulse;this.spin=spin;sourceUser=user;
        }
        public static Ammo fromSourceName(String name){for(var p:values())if(p.sourceName.equals(name))return p;return null;}
    }
    private final Ammo ammo;
    private final User user;
    private Vec3 heading,velocity;
    private double seconds,friction;
    private boolean brakeComplete,trackingGate=true,lost;

    public GreeneMissileFlight(Ammo ammo,Vec3 direction,Vec3 inheritedVelocity){this(ammo,ammo==null?null:ammo.sourceUser,direction,inheritedVelocity);}
    public GreeneMissileFlight(Ammo ammo,User user,Vec3 direction,Vec3 inheritedVelocity){
        if(ammo==null||user==null||!finite(direction)||direction.lengthSqr()<1e-10||!validVelocity(inheritedVelocity))throw new IllegalArgumentException("Invalid Greene missile launch");
        this.ammo=ammo;this.user=user;heading=direction.normalize();velocity=heading.scale(ammo.initialSpeed).add(inheritedVelocity);
    }
    public Ammo ammo(){return ammo;}public User user(){return user;}
    public Vec3 heading(){return heading;}public Vec3 velocity(){return velocity;}
    public double ageSeconds(){return seconds;}public boolean brakeComplete(){return brakeComplete;}public boolean lostGuidance(){return lost;}
    public void loseTarget(){lost=true;}
    /** Native 0x104699b0 uses strict > and launch displacement, not path length.
     * The source's absolute-Y75/300-unit range exception is not mapped onto MC terrain. */
    public boolean expired(){return seconds>ammo.lifetime;}
    public boolean outOfRange(Vec3 launch,Vec3 position){return !finite(launch)||!finite(position)||launch.distanceToSqr(position)>RANGE*RANGE;}
    /** Proximity is a target-origin trigger, not an area blast or visual radius. */
    public boolean proximityTriggered(Vec3 position,Vec3 originalTargetOrigin){
        return ammo.proximity>0&&finite(position)&&finite(originalTargetOrigin)&&position.distanceToSqr(originalTargetOrigin)<ammo.proximity*ammo.proximity;
    }
    public Vec3 step(Vec3 position,Vec3 target,Vec3 targetVelocity,double dt){return step(position,target,targetVelocity,Vec3.ZERO,dt);}
    /** Caller owns target selection and transforms the original shot-error vector
     * into world space each update. Zero error is explicit, not a reconstructed
     * accuracy distribution. Perfect tracking skips that error, not intercept lead. */
    public Vec3 step(Vec3 position,Vec3 target,Vec3 targetVelocity,Vec3 targetWorldError,double dt){
        if(expired()||!finite(position)||!Double.isFinite(dt)||dt<=0)return Vec3.ZERO;
        seconds+=dt;
        if(expired())return Vec3.ZERO;
        if(ammo.tracking&&!lost){
            // Source +E1 starts true (all six startTrackingWhenHigher=false).
            // A missing aim point drops this gate; a later target below can reopen it.
            if(!trackingGate&&finite(target)&&position.y>target.y)trackingGate=true;
            if(trackingGate&&seconds>=ammo.trackingDelay){
                if(!finite(target)){trackingGate=false;}
                else{
                    Vec3 aim=ammo.perfectTracking||!finite(targetWorldError)?target:target.add(targetWorldError);
                    Vec3 motion=validVelocity(targetVelocity)?targetVelocity:Vec3.ZERO;
                    Vec3 predicted=user.deadReckoning?lead(position,aim,motion,velocity.length()):aim;
                    Vec3 desired=directionOrHeading(predicted.subtract(position));
                    // Both guided mother profiles have minDotBetweenHeadings=-1.
                    if(heading.dot(desired)<=-1)desired=directionOrHeading(aim.subtract(position));
                    if(heading.dot(desired)<=-1)lost=true;
                    else heading=turn(heading,desired,ammo.turnRate*dt);
                }
            }
        }
        boolean braking=ammo.initialDeceleration>0&&!brakeComplete;
        accelerate(braking?0:ammo.speed,braking?ammo.initialDeceleration:ammo.acceleration,dt);
        if(braking&&velocity.lengthSqr()<BRAKE_DONE_SPEED_SQUARED)brakeComplete=true;
        // All six profiles have gravity=0. No vanilla gravity/drag belongs here.
        Vec3 move=velocity.scale(dt);
        // 1046c0c0 writes +A4 from actual displacement, including inherited
        // drift. 1046c680 updates previous position after the collision query.
        // Sub-epsilon motion preserves the current guidance direction.
        if(Math.abs(move.x)>SOURCE_MOVEMENT_EPSILON||Math.abs(move.y)>SOURCE_MOVEMENT_EPSILON||Math.abs(move.z)>SOURCE_MOVEMENT_EPSILON)heading=move.scale(1/move.length());
        return move;
    }
    private void accelerate(double targetSpeed,double acceleration,double dt){
        double forward=velocity.dot(heading);Vec3 sideways=velocity.subtract(heading.scale(forward));double slip=sideways.length();
        if(slip>1e-10)velocity=velocity.subtract(sideways.scale(Math.min(slip,friction*dt)/slip));
        friction+=FRICTION_GAIN*dt;
        // 0x10468430 measures magnitude of the parallel component. Acceleration
        // <=0 means immediate speed correction, not disabled thrust.
        double change=targetSpeed-Math.abs(forward);
        if(acceleration>0)change=Math.max(-acceleration*dt,Math.min(acceleration*dt,change));
        velocity=velocity.add(heading.scale(change));
    }
    private Vec3 directionOrHeading(Vec3 v){return v.lengthSqr()<1e-10?heading:v.normalize();}
    static Vec3 lead(Vec3 position,Vec3 target,Vec3 motion,double speed){
        if(speed<=1e-8)return target;
        Vec3 delta=target.subtract(position);double c=delta.lengthSqr(),b=2*delta.dot(motion),a=motion.lengthSqr()-speed*speed;
        double discriminant=b*b-4*a*c;if(discriminant<=0)return target;
        double time=2*c/(Math.sqrt(discriminant)-b);
        return Double.isFinite(time)&&time>0?target.add(motion.scale(time)):target;
    }
    private static Vec3 turn(Vec3 from,Vec3 to,double maximum){
        double dot=Math.max(-1,Math.min(1,from.dot(to))),angle=Math.acos(dot);
        if(angle<1e-6||maximum>=angle)return to;
        if(maximum<=0)return from;
        Vec3 axis=from.cross(to).normalize();if(axis.lengthSqr()<1e-10)return from;
        return from.scale(Math.cos(maximum)).add(axis.cross(from).scale(Math.sin(maximum))).normalize();
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.lengthSqr());}
    private static boolean validVelocity(Vec3 v){return finite(v)&&v.lengthSqr()<=4096*4096;}
    private static void put(CompoundTag tag,String key,Vec3 v){tag.putDouble(key+"X",v.x);tag.putDouble(key+"Y",v.y);tag.putDouble(key+"Z",v.z);}
    private static Vec3 read(CompoundTag tag,String key){
        if(!tag.contains(key+"X",6)||!tag.contains(key+"Y",6)||!tag.contains(key+"Z",6))return null;
        return new Vec3(tag.getDouble(key+"X"),tag.getDouble(key+"Y"),tag.getDouble(key+"Z"));
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putString("Ammo",ammo.sourceName);tag.putString("User",user.sourceName);
        put(tag,"Heading",heading);put(tag,"Velocity",velocity);tag.putDouble("Seconds",seconds);tag.putDouble("Friction",friction);
        tag.putBoolean("Braked",brakeComplete);tag.putBoolean("TrackingGate",trackingGate);tag.putBoolean("Lost",lost);return tag;
    }
    public static GreeneMissileFlight load(CompoundTag tag){
        Ammo ammo=Ammo.fromSourceName(tag.getString("Ammo"));User user=User.fromSourceName(tag.getString("User"));
        Vec3 heading=read(tag,"Heading"),velocity=read(tag,"Velocity");double seconds=tag.getDouble("Seconds"),friction=tag.getDouble("Friction");
        if(tag.getInt("Version")!=1||ammo==null||user==null||!finite(heading)||Math.abs(heading.lengthSqr()-1)>1e-4||!validVelocity(velocity)
                ||!tag.contains("Seconds",6)||!tag.contains("Friction",6)||!tag.contains("TrackingGate",1)||!tag.contains("Braked",1)||!tag.contains("Lost",1)||!Double.isFinite(seconds)||seconds<0||seconds>1e6
                ||!Double.isFinite(friction)||friction<0||friction>FRICTION_GAIN*seconds+1e-6)return null;
        var result=new GreeneMissileFlight(ammo,user,heading,Vec3.ZERO);result.velocity=velocity;result.seconds=seconds;result.friction=friction;
        result.brakeComplete=tag.getBoolean("Braked");result.trackingGate=tag.getBoolean("TrackingGate");result.lost=tag.getBoolean("Lost");return result;
    }
}
