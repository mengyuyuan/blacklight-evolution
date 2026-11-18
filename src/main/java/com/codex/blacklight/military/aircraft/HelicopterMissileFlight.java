package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** P1 Hellfire flight, sampled at Minecraft's 20 Hz. See helicopter-hellfire-source.json.
 * Internal velocity is blocks/second; step() returns blocks/tick for native collision.
 * Heading and sideways velocity are separate: turning must not erase inherited drift. */
public final class HelicopterMissileFlight {
    public static final double DT=.05,SPEED=60,ACCELERATION=50,FRICTION_GAIN=50,RANGE=175;
    public static final int IGNITION_TICKS=2,TRACKING_TICKS=10,LIFETIME_TICKS=200;
    private final boolean player;
    private Vec3 heading,velocity,coldVelocity;
    private int age;
    private double friction;
    private boolean lost;
    public HelicopterMissileFlight(Vec3 direction,Vec3 inheritedBlocksPerTick,boolean player){
        if(!finite(direction)||direction.lengthSqr()<1e-10||!velocityValid(inheritedBlocksPerTick.scale(20)))throw new IllegalArgumentException("Invalid Hellfire launch");
        this.player=player;heading=direction.normalize();velocity=inheritedBlocksPerTick.scale(20);coldVelocity=velocity;
    }
    public int age(){return age;}public boolean lost(){return lost;}public boolean player(){return player;}
    public Vec3 heading(){return heading;}public Vec3 velocity(){return velocity;}
    public boolean expired(){return age>=LIFETIME_TICKS;}
    public void loseTarget(){lost=true;}
    public Vec3 step(Vec3 position,Vec3 target,Vec3 targetVelocity){
        if(expired()||!finite(position))return Vec3.ZERO;
        // Global missile age advances before the Javelin physics callback. Its
        // separate ignition age advances after the callback in the original.
        if(age<IGNITION_TICKS){
            coldVelocity=coldVelocity.add(0,-28*DT,0);age++;
            if(age==IGNITION_TICKS)velocity=heading.scale(velocity.length());
            return coldVelocity.scale(DT);
        }
        if(!lost&&age+1>=TRACKING_TICKS){
            if(!finite(target)||!velocityValid(targetVelocity))lost=true;
            else{
                Vec3 predicted=lead(position,target,targetVelocity,velocity.length());
                Vec3 desired=predicted.subtract(position).normalize();double minimum=player?-.3:0;
                // The source falls back from a rejected intercept heading to the
                // direct heading once, then permanently releases this track.
                if(desired.lengthSqr()<1e-10)desired=heading;
                if(heading.dot(desired)<=minimum)desired=target.subtract(position).normalize();
                if(desired.lengthSqr()<1e-10)desired=heading;
                if(heading.dot(desired)<=minimum)lost=true;
                else heading=turn(heading,desired,(player?10:6)*DT*velocity.length()/SPEED);
            }
        }
        double forward=velocity.dot(heading);
        Vec3 sideways=velocity.subtract(heading.scale(forward));double slip=sideways.length();
        if(slip>1e-10)velocity=velocity.subtract(sideways.scale(Math.min(slip,friction*DT)/slip));
        friction+=FRICTION_GAIN*DT;
        // Original acceleration helper uses the magnitude of the parallel component.
        double change=Math.max(-ACCELERATION*DT,Math.min(ACCELERATION*DT,SPEED-Math.abs(forward)));
        velocity=velocity.add(heading.scale(change));age++;
        return velocity.scale(DT);
    }
    static Vec3 lead(Vec3 position,Vec3 target,Vec3 targetVelocity,double speed){
        if(speed<=1e-8)return target;
        Vec3 delta=target.subtract(position);double c=delta.lengthSqr(),b=2*delta.dot(targetVelocity),a=targetVelocity.lengthSqr()-speed*speed;
        double discriminant=b*b-4*a*c;
        if(discriminant<=0)return target;
        // Same rationalized positive intercept root as 0x101262f0.
        double time=2*c/(Math.sqrt(discriminant)-b);
        return Double.isFinite(time)&&time>0?target.add(targetVelocity.scale(time)):target;
    }
    static Vec3 turn(Vec3 from,Vec3 to,double maximum){
        double dot=Math.max(-1,Math.min(1,from.dot(to))),angle=Math.acos(dot);
        if(angle<1e-6||maximum>=angle)return to;
        if(maximum<=0)return from;
        Vec3 axis=from.cross(to).normalize();
        if(axis.lengthSqr()<1e-10)return from;
        return from.scale(Math.cos(maximum)).add(axis.cross(from).scale(Math.sin(maximum))).normalize();
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.lengthSqr());}
    private static boolean velocityValid(Vec3 v){return finite(v)&&v.lengthSqr()<=128*128;}
    private static void vector(CompoundTag tag,String key,Vec3 value){tag.putDouble(key+"X",value.x);tag.putDouble(key+"Y",value.y);tag.putDouble(key+"Z",value.z);}
    private static Vec3 vector(CompoundTag tag,String key){
        if(!tag.contains(key+"X",6)||!tag.contains(key+"Y",6)||!tag.contains(key+"Z",6))return null;
        return new Vec3(tag.getDouble(key+"X"),tag.getDouble(key+"Y"),tag.getDouble(key+"Z"));
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putBoolean("Player",player);tag.putInt("Age",age);tag.putDouble("Friction",friction);tag.putBoolean("Lost",lost);
        vector(tag,"Heading",heading);vector(tag,"Velocity",velocity);vector(tag,"Cold",coldVelocity);return tag;
    }
    public static HelicopterMissileFlight load(CompoundTag tag){
        Vec3 heading=vector(tag,"Heading"),velocity=vector(tag,"Velocity"),cold=vector(tag,"Cold");
        int age=tag.getInt("Age");double friction=tag.getDouble("Friction");
        if(tag.getInt("Version")!=1||!finite(heading)||Math.abs(heading.lengthSqr()-1)>1e-4||!velocityValid(velocity)||!velocityValid(cold)
                ||age<0||age>LIFETIME_TICKS||!tag.contains("Friction",6)||!Double.isFinite(friction)||friction<0||friction>FRICTION_GAIN*LIFETIME_TICKS*DT)return null;
        var flight=new HelicopterMissileFlight(heading,Vec3.ZERO,tag.getBoolean("Player"));flight.velocity=velocity;flight.coldVelocity=cold;
        flight.age=age;flight.friction=friction;flight.lost=tag.getBoolean("Lost");return flight;
    }
}
