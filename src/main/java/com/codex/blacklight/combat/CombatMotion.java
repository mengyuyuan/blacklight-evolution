package com.codex.blacklight.combat;

import com.google.gson.*;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Motion callbacks are separate from clip/contact time. Native numerical inputs
 * may be installed locally; the view-wide approach and Minecraft unit conversion
 * are explicit adaptation policies, not a reconstruction of the original engine. */
public final class CombatMotion {
    public static final String FILE="combat-motion-tuning.data";
    private static final JsonObject LOCAL=load();
    private CombatMotion(){}
    private static JsonObject load(){
        return MotionResources.read(FILE,1024*1024).orElseGet(JsonObject::new);
    }
    private static JsonObject fields(WeaponMove move,String phase){
        var moves=LOCAL.getAsJsonObject("moves");
        if(moves==null||!moves.has(move.id))return new JsonObject();
        var item=moves.getAsJsonObject(move.id);
        return item.has(phase)?item.getAsJsonObject(phase):new JsonObject();
    }
    private static double value(JsonObject json,String name,double fallback){
        if(!json.has(name))return fallback;
        double v=json.get(name).getAsDouble();
        if(!Double.isFinite(v))throw new IllegalArgumentException("Non-finite combat field "+name);
        return v;
    }
    public static double turn(WeaponMove move,boolean windup){
        return Math.clamp(value(fields(move.uncharged(),windup?"windup":"release"),"TurningVelocity",windup?900:360)/20,0,180);
    }
    public static boolean usesDirectionalInput(WeaponMove move){
        return !move.aerial()&&value(fields(move.uncharged(),"windup"),"UseInput",1)!=0;
    }
    /** Finite release tracking windows are separate from the damage window. */
    public static boolean releaseCanTurn(WeaponMove move,double seconds){
        var f=fields(move.uncharged(),"release");
        double begin=value(f,"TimeBegin",0),end=value(f,"TimeEnd",-1);
        // Unresolved/unavailable end markers retain the surrounding action's limit.
        return seconds>=begin-.0001&&(end<0||seconds<end-.0001);
    }
    public static double speed(WeaponMove move,float charge){
        double min=move==WeaponMove.AIR_KICK?25:move==WeaponMove.AIR_PAIN?35:move.airCombo()?10:move.landingAttack()?30:move.aerial()?24:14;
        double max=move==WeaponMove.AIR_KICK?50:move==WeaponMove.AIR_PAIN?70:min;
        var f=fields(move,"flight");
        min=value(f,"VelocityMin",value(f,"Velocity",value(f,"VelocityXZMax",min)));
        max=value(f,"VelocityMax",f.has("Velocity")||f.has("VelocityXZMax")?min:max);
        // Original rates use seconds. The .87 retarget scale is shared with the
        // installed skeleton; this is a Minecraft conversion, not raw game metres.
        return Math.clamp((min+(max-min)*charge)*.87/20,.35,3.1);
    }
    public static double acceleration(WeaponMove move){
        return Math.clamp(value(fields(move,"flight"),"AccelerationXZ",50)*.87/400,.06,.4);
    }
    public static double gravity(WeaponMove move){return Math.clamp(value(fields(move,"flight"),"Gravity",40)*.87/400,.04,.15);}
    public static boolean freeFlight(WeaponMove move){return move==WeaponMove.AIR_KICK||move==WeaponMove.AIR_PAIN;}
    /** Only actual aerial pursuit moves may bridge a distant target gap.
     * A grounded combo finisher's small hop is not a travel attack. */
    public static boolean stationaryWhip(WeaponMove move){return move!=null&&move.uncharged()==WeaponMove.WHIP_AIR;}
    public static boolean canApproach(WeaponMove move){
        return move.aerial()&&!move.airCombo()&&!stationaryWhip(move)
                &&move!=WeaponMove.AIR_CLAP&&move.uncharged()!=WeaponMove.HAMMER_COMBO_SPECIAL;
    }
    public static int flightTicks(WeaponMove move,float charge){
        if(!freeFlight(move))return 0;
        var f=fields(move,"flight");
        double min=value(f,"TimeEndMin",move==WeaponMove.AIR_KICK?1.0/3:2.0/3);
        double max=value(f,"TimeEndMax",move==WeaponMove.AIR_KICK?.5:7.0/6);
        return Math.clamp((int)Math.ceil((min+(max-min)*Math.clamp(charge,0,1))*20-.0001),1,60);
    }
    /** Free attacks commit to a release heading. Camera pitch is bounded for the
     * Minecraft adaptation; it is not a claim about the native vector transform. */
    public static Vec3 flightDirection(float yaw,float pitch){return Vec3.directionFromRotation(Math.clamp(pitch,-20,35),yaw);}
    public static Vec3 tossVelocity(float charge,Vec3 direction){
        var j=fields(WeaponMove.HAMMER_RAM,"jump");
        double distance=value(j,"DistanceMin",45)+(value(j,"DistanceMax",130)-value(j,"DistanceMin",45))*charge;
        double angle=Math.toRadians(value(j,"LaunchAngle",60));
        double cap=value(j,"MaxInitialVelocityMin",45)+(value(j,"MaxInitialVelocityMax",75)-value(j,"MaxInitialVelocityMin",45))*charge;
        double speed=Math.min(cap,Math.sqrt(distance*value(j,"Gravity",45)/Math.max(.1,Math.sin(2*angle))))*.87/20;
        return direction.scale(speed*Math.cos(angle)).add(0,speed*Math.sin(angle),0);
    }
    public static double tossGravity(){return value(fields(WeaponMove.HAMMER_RAM,"jump"),"Gravity",45)*.87/400;}
    public static boolean directionalDive(WeaponMove move){return move==WeaponMove.BLADE_DIVE||move==WeaponMove.HAMMER_DROP;}
    public static double bladeRushStep(float charge,double fromRelease,double toRelease){
        var f=fields(WeaponMove.BLADE_RUSH,"displacement");
        // Callback times belong to the release, while the installed pose includes
        // its anticipation and transition. Integrate partial boundary ticks so a
        // fractional release seek cannot add or lose a full rush step.
        double begin=value(f,"TimeBegin",1.0/15),end=value(f,"TimeEnd",17.0/30);
        double overlap=Math.max(0,Math.min(toRelease,end)-Math.max(fromRelease,begin));
        if(overlap==0||end<=begin)return 0;
        double min=f.has("DistanceMin")?Math.abs(f.getAsJsonArray("DistanceMin").get(2).getAsDouble()):7;
        double max=f.has("DistanceMax")?Math.abs(f.getAsJsonArray("DistanceMax").get(2).getAsDouble()):10;
        return (min+(max-min)*Math.clamp(charge,0,1))*.87*overlap/(end-begin);
    }
    public static float travelPose(WeaponMove move){
        if(move==WeaponMove.CLAW_POUNCE)return .4F;
        var local=LocalWeaponMotion.find(move.id);
        if(move.landingAttack())return local.flatMap(LocalWeaponMotion.Definition::landing).map(LocalWeaponMotion.Landing::holdTime).orElse((move.start-2)/20F);
        // Last anticipation sample, before any real damage window.
        return Math.max(0,(move.start-2)/20F);
    }
    public static double standOff(WeaponMove move){
        if(move.form==PowerForm.WHIPFIST)return move.uncharged()==WeaponMove.WHIP_SWEEP?3.2:4.5;
        if(move.landingAttack())return .6;
        return 1.15;
    }
    public static int travelBudget(double distance,double speed){return Math.clamp((int)Math.ceil(distance/Math.max(.35,speed))+60,60,640);}
    public static Vec3 chase(Vec3 delta,double standOff,double speed){
        double length=delta.horizontalDistance();
        return length<.001?Vec3.ZERO:delta.multiply(1,0,1).scale(Math.min(speed,Math.max(0,length-standOff)*.55)/length);
    }
    public static double brakeGravity(WeaponMove move,boolean rising){
        var b=fields(move,"brake");return Math.clamp(value(b,rising?"GravityUp":"Gravity",0)*.87/400,0,.15);
    }
    public static double brakeHorizontal(WeaponMove move){return Math.clamp(value(fields(move,"brake"),"VelocityXZ",10)*.87/20,0,2.2);}
    public static double whip(String branch,String name,double fallback){
        var root=LOCAL.getAsJsonObject("whipGrab");
        return root!=null&&root.has(branch)?value(root.getAsJsonObject(branch),name,fallback):fallback;
    }
}
