package com.codex.blacklight.military.vehicle;

import net.minecraft.world.phys.Vec3;

/** Bounded presentation impulses. Never changes the authoritative hull, aim or damage. */
public final class VehicleFeedbackMotion {
    public enum Impact { BULLET, HEAVY, EXPLOSIVE }
    public record Pose(double pitch,double roll,double heave,double primaryRecoil,double secondaryRecoil) {}
    public static double strength(float damage,float health,Impact kind){
        if(!Float.isFinite(damage)||!Float.isFinite(health)||damage<=0||health<=0)return 0;
        double fraction=Math.sqrt(Math.min(1,damage/health));
        return kind==Impact.BULLET?Math.min(.15,.035+fraction*.35):Math.min(1.2,.18+fraction*(kind==Impact.EXPLOSIVE?3.2:2.8));
    }
    public static double recoil(double age){
        if(!Double.isFinite(age)||age<0||age>=12)return 0;
        // Fast compression, a visibly slower hydraulic return; no kick before a shot.
        return age<.65?age/.65:Math.pow(Math.max(0,1-(age-.65)/11.35),2);
    }
    public static double spring(double age){
        if(!Double.isFinite(age)||age<0||age>=18)return 0;
        double end=1-age/18;
        return Math.sin(age*.85)*Math.exp(-age*.19)*end*end;
    }
    public static Pose sample(String weapon,double shotAge,double secondaryAge,double turretYaw,
                              double hitAge,double strength,Vec3 localPush){
        return sample(weapon,shotAge,secondaryAge,turretYaw,hitAge,strength,localPush,Vec3.ZERO);
    }
    /** Contact X/Z is normalized by the chassis half width/length, relative to its center. */
    public static Pose sample(String weapon,double shotAge,double secondaryAge,double turretYaw,
                              double hitAge,double strength,Vec3 localPush,Vec3 contact){
        double primary=weapon.equals("tank_he")||weapon.equals("tank_ap")?.42:weapon.equals("autocannon")?.13:0;
        double kick=recoil(shotAge),secondary=recoil(secondaryAge),a=Math.toRadians(turretYaw);
        double response=spring(hitAge)*Math.clamp(Double.isFinite(strength)?strength:0,0,1.2);
        Vec3 push=localPush!=null&&Double.isFinite(localPush.lengthSqr())?localPush.normalize():Vec3.ZERO;
        Vec3 lever=contact!=null&&Double.isFinite(contact.lengthSqr())?new Vec3(Math.clamp(contact.x,-1,1),0,Math.clamp(contact.z,-1,1)):Vec3.ZERO;
        double fire=spring(shotAge)*(primary>.2?1.25:primary>0?.32:0);
        // Positive-Y model coordinates: positive X rotation tips the roof toward +Z;
        // positive Z rotation tips it toward -X. Firing pushes opposite the barrel.
        double pitch=Math.clamp((push.z-lever.z*push.y)*response*5.2-Math.cos(a)*fire,-4.5,4.5);
        double roll=Math.clamp((-push.x+lever.x*push.y)*response*5.2+Math.sin(a)*fire,-4.5,4.5);
        double heave=Math.clamp(response*push.y*.13-Math.abs(response)*.055-Math.abs(fire)*.013,-.16,.10);
        return new Pose(pitch,roll,heave,primary*kick,.025*secondary);
    }
    private VehicleFeedbackMotion(){}
}
