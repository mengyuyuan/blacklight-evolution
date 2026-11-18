package com.codex.blacklight.military.vehicle;

/** Bounded mixer, independent of player vs AI control and faction livery. */
public final class VehicleAudioState {
    public record Mix(float engine,float rolling,float enginePitch,float rollingPitch) {}
    public static float load(double distance,float yaw,double maxSpeed){
        if(!Double.isFinite(distance)||!Float.isFinite(yaw)||!Double.isFinite(maxSpeed)||maxSpeed<=0)return 0;
        return (float)net.minecraft.util.Mth.clamp(Math.max(Math.abs(distance)/maxSpeed,Math.abs(yaw)/3*.55),0,1);
    }
    public static Mix mix(boolean running,float drive,String chassis){
        float load=Float.isFinite(drive)?net.minecraft.util.Mth.clamp(drive,0,1):0;
        float base=chassis.equals("tank")?.78F:chassis.equals("apc")?.88F:1F;
        return new Mix(running?.23F+load*.42F:0,running&&load>.015F?.60F*load:0,base+load*.45F,.65F+load*.75F);
    }
    public static float approach(float current,float target){return Math.abs(current-target)<.003F?target:current+(target-current)*(target>current?.22F:.38F);}
    private VehicleAudioState(){}
}
