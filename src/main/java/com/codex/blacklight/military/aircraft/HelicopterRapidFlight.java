package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Ordinary P1 70mm MissileBehaviour; unlike Hellfire there is no cold/drop or tracking phase. */
public final class HelicopterRapidFlight {
    public static final int LIFETIME=300;
    public static final double RANGE=175,LENGTH=1.4527442455291748,INITIAL_SPEED=150,SPEED=125;
    private final Vec3 heading;
    private Vec3 velocity;
    private double friction=10;
    private int age;
    public HelicopterRapidFlight(Vec3 direction,Vec3 inherited){
        if(!finite(direction)||direction.lengthSqr()<1e-8||!finite(inherited)||inherited.lengthSqr()>64*64)throw new IllegalArgumentException("Invalid rapid rocket launch");
        heading=direction.normalize();velocity=heading.scale(INITIAL_SPEED).add(inherited.scale(20));
    }
    public Vec3 heading(){return heading;}public int age(){return age;}public boolean expired(){return age>=LIFETIME;}
    public Vec3 step(){
        if(expired())return Vec3.ZERO;
        double forward=velocity.dot(heading);Vec3 sideways=velocity.subtract(heading.scale(forward));double slip=sideways.length();
        if(slip>1e-10)velocity=velocity.subtract(sideways.scale(Math.min(slip,friction*.05)/slip));
        friction+=50*.05;age++;
        // 0x104685ae: acceleration <= 0 takes the immediate correction branch,
        // while positive acceleration is rate-limited. Initial150 becomes125 on
        // the first ordinary flight update; inherited sideways drift is separate.
        velocity=velocity.add(heading.scale(SPEED-Math.abs(forward)));
        return velocity.scale(.05);
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.lengthSqr());}
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Version",1);tag.putInt("Age",age);tag.putDouble("Friction",friction);
        put(tag,"Heading",heading);put(tag,"Velocity",velocity);return tag;
    }
    private static void put(CompoundTag tag,String key,Vec3 v){tag.putDouble(key+"X",v.x);tag.putDouble(key+"Y",v.y);tag.putDouble(key+"Z",v.z);}
    private static Vec3 read(CompoundTag tag,String key){
        if(!tag.contains(key+"X",6)||!tag.contains(key+"Y",6)||!tag.contains(key+"Z",6))return null;
        return new Vec3(tag.getDouble(key+"X"),tag.getDouble(key+"Y"),tag.getDouble(key+"Z"));
    }
    public static HelicopterRapidFlight load(CompoundTag tag){
        if(tag.getInt("Version")!=1||!tag.contains("Age",3)||!tag.contains("Friction",6))return null;
        var heading=read(tag,"Heading");var velocity=read(tag,"Velocity");int age=tag.getInt("Age");double friction=tag.getDouble("Friction");
        if(!finite(heading)||Math.abs(heading.lengthSqr()-1)>.001||!finite(velocity)||velocity.lengthSqr()>1500*1500||age<0||age>LIFETIME||!Double.isFinite(friction)||Math.abs(friction-(10+age*2.5))>.001)return null;
        var result=new HelicopterRapidFlight(heading,Vec3.ZERO);result.velocity=velocity;result.age=age;result.friction=friction;return result;
    }
}
