package com.codex.blacklight.military.aircraft;

import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;

/** P1 CircleStrafeAction 101e17a0: tangent plus radial correction, with a terrain-raised height band. */
public final class HelicopterCircleStrafe {
    public static final double MIN_DISTANCE=20,MAX_DISTANCE=30,MIN_SPEED=.6,MAX_SPEED=.8;
    public record Motion(Vec3 request,double minimumHeight,double maximumHeight,boolean inBand){}
    private double speed;
    public double speed(){return speed;}
    public void reset(){speed=0;}
    public void begin(double roll){if(!Double.isFinite(roll)||roll<0||roll>=1)throw new IllegalArgumentException("Circle speed sample");speed=MIN_SPEED+(MAX_SPEED-MIN_SPEED)*roll;}
    public boolean initialized(){return speed>=MIN_SPEED&&speed<=MAX_SPEED;}
    /** Minecraft terrain can raise a valid source orbit outside its own gun
     * envelope. Keep both authored bounds: choose the shootable part of the
     * 20..30 orbit/height band, or let the tactic bank choose another maneuver.
     * This is a navigation adapter, not an increase to weapon range or angles. */
    public static boolean firingBandAvailable(double targetY,double terrainTop,boolean hydra){
        if(!Double.isFinite(targetY)||!Double.isFinite(terrainTop))return false;
        double safe=hydra?15:6,rise=Math.max(0,terrainTop-targetY);
        double low=rise>safe?rise+safe:safe;
        return low<=(MAX_DISTANCE-1)/Math.sqrt(2);
    }
    public Motion firingMotion(Vec3 position,Vec3 target,double terrainTop,boolean hydra,Vec3 fallback){
        var source=motion(position,target,terrainTop,hydra,fallback);
        if(source==null||!firingBandAvailable(target.y,terrainTop,hydra))return null;
        double high=Math.min(source.maximumHeight(),(MAX_DISTANCE-1)/Math.sqrt(2));
        double height=position.y-target.y;
        // Native ordinary pitch <=30 gives r >= sqrt(2)*abs(height).
        // One block of aiming margin still stays inside the source orbit.
        double minimum=Math.max(MIN_DISTANCE,Math.min(MAX_DISTANCE,Math.sqrt(2)*Math.max(source.minimumHeight(),Math.min(high,height))+1));
        Vec3 inward=target.subtract(position).multiply(1,0,1);double distance=inward.length();
        inward=distance<1e-8?fallback.multiply(1,0,1).normalize():inward.scale(1/distance);
        if(inward.lengthSqr()<1e-8)inward=new Vec3(0,0,1);
        Vec3 tangent=new Vec3(-inward.z,0,inward.x);
        if(distance<minimum)tangent=tangent.subtract(inward.scale(minimum-distance));
        else if(distance>MAX_DISTANCE)tangent=tangent.add(inward.scale(distance-MAX_DISTANCE));
        Vec3 request=tangent.normalize().scale(speed);
        if(height<source.minimumHeight())request=request.add(0,source.minimumHeight()-height,0);
        else if(height>high)request=request.add(0,high-height,0);
        return new Motion(request,source.minimumHeight(),high,
                distance>=minimum-1&&distance<=MAX_DISTANCE+1&&height>=source.minimumHeight()-1&&height<=high+1);
    }
    public Motion motion(Vec3 position,Vec3 target,double terrainTop,boolean hydra,Vec3 fallback){
        if(!initialized()||!HelicopterFlight.finite(position)||!HelicopterFlight.finite(target)||!Double.isFinite(terrainTop)||!HelicopterFlight.finite(fallback))return null;
        double low=hydra?15:6,high=hydra?20:10,safe=low,rise=Math.max(0,terrainTop-target.y);
        if(rise>low){high=rise+safe+(high-low);low=rise+safe;}
        Vec3 inward=target.subtract(position).multiply(1,0,1);double distance=inward.length();
        // A coincident XZ position has no tangent. Pick the aircraft heading only
        // for that Minecraft degeneracy, then let the source distance correction act.
        inward=distance<1e-8?fallback.multiply(1,0,1).normalize():inward.scale(1/distance);
        if(inward.lengthSqr()<1e-8)inward=new Vec3(0,0,1);
        Vec3 tangent=new Vec3(-inward.z,0,inward.x);
        if(distance<MIN_DISTANCE)tangent=tangent.subtract(inward.scale(MIN_DISTANCE-distance));
        else if(distance>MAX_DISTANCE)tangent=tangent.add(inward.scale(distance-MAX_DISTANCE));
        Vec3 request=tangent.normalize().scale(speed);double height=position.y-target.y;
        if(height<low)request=request.add(0,low-height,0);
        else if(height>high)request=request.add(0,high-height,0);
        // One-block phase tolerance belongs to MC navigation, not the source band.
        return new Motion(request,low,high,distance>=MIN_DISTANCE-1&&distance<=MAX_DISTANCE+1&&height>=low-1&&height<=high+1);
    }
    public CompoundTag save(){var tag=new CompoundTag();tag.putInt("Version",1);tag.putDouble("Speed",speed);return tag;}
    public void load(CompoundTag tag){reset();if(tag.getInt("Version")==1&&tag.contains("Speed",Tag.TAG_DOUBLE)){double value=tag.getDouble("Speed");if(Double.isFinite(value)&&value>=MIN_SPEED&&value<=MAX_SPEED)speed=value;}}
}
