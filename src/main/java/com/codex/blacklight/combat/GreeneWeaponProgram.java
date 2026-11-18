package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original MotherCore clip slices, Execute-relative Shoot requests and the
 * startup FIG's ordered on-foot shoot bank. The outer encounter AI is separate. */
public enum GreeneWeaponProgram {
    DEATH_FLOWER,SHOTGUN,INSECT_SWARM,RIFLE,ANTI_TANK,ANTI_AIR;
    public record Shot(int slot,double begin,double end,GreeneMissileFlight.Ammo ammo,GreeneMissileFlight.User user,
                       boolean fixed,String space,float[] euler) {
        public Shot {
            if(slot<1||slot>10||!Double.isFinite(begin)||!Double.isFinite(end)||begin<0||end<=begin||ammo==null||user==null||euler==null||euler.length!=3)
                throw new IllegalArgumentException("Invalid original Greene Shoot track");
            for(float angle:euler)if(!Float.isFinite(angle))throw new IllegalArgumentException("Invalid shot Euler angle");
            // 0x101122e0 uses the spawned weapon as context, with an empty joint
            // hash. JointSpace therefore falls back to that same attached weapon
            // matrix as GameObjectSpace. Other spaces are not silently conflated.
            if(fixed&&!"JointSpace".equals(space)&&!"GameObjectSpace".equals(space))throw new IllegalArgumentException("Unported Greene direction space");
        }
        // Source float times near a 20 Hz boundary are snapped only within 20 us.
        public boolean active(int age){return age>=Math.ceil(begin*20-.0004)&&age<Math.ceil(end*20-.0004);}
    }
    private SourceBossAction action;
    private List<Shot> shots;
    static {
        try(var in=Objects.requireNonNull(GreeneWeaponProgram.class.getResourceAsStream("/assets/blacklight/models/greene_weapon_programs.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var gson=new Gson();
            for(var entry:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("programs")){
                var row=entry.getAsJsonObject();var program=valueOf(row.get("id").getAsString());
                program.action=SourceBossAction.decode(row.getAsJsonObject("action"));var shots=new ArrayList<Shot>();
                for(var item:row.getAsJsonArray("shots")){
                    var s=item.getAsJsonObject();var ammo=Objects.requireNonNull(GreeneMissileFlight.Ammo.fromSourceName(s.get("ammo").getAsString()));
                    var user=Objects.requireNonNull(GreeneMissileFlight.User.fromSourceName(s.get("user").getAsString()));
                    shots.add(new Shot(s.get("slot").getAsInt(),s.get("begin").getAsDouble(),s.get("end").getAsDouble(),ammo,user,
                        s.get("fixed").getAsBoolean(),s.get("space").getAsString(),gson.fromJson(s.get("euler"),float[].class)));
                }
                program.shots=List.copyOf(shots);
            }
            for(var p:values())if(p.action==null||p.shots==null||p.shots.isEmpty())throw new IllegalStateException("Missing Greene program "+p);
        }catch(Exception e){throw new IllegalStateException("Invalid Greene weapon programs",e);}
    }
    public SourceBossAction action(){return action;}
    public List<Shot> shots(){return shots;}
    public boolean antiVehicle(){return this==ANTI_TANK||this==ANTI_AIR;}
    /** Timer 26b21f8d9bf95838 is set only by ShotgunBlast OnEnter (271680).
     * Mapping successful MC begin to that event is the explicit scheduler adapter. */
    public int shootGateTicks(){return this==SHOTGUN?54:0;}
    public static GreeneWeaponProgram byId(int id){return id>=0&&id<values().length?values()[id]:DEATH_FLOWER;}
    public static final double ON_FOOT_RANGE=150;
    public static boolean onFootRange(double distance){return Double.isFinite(distance)&&distance>=0&&distance<ON_FOOT_RANGE;}
    /** startup FIG 267864 parent (<150 XZ), then 269988/270612/271236/271908.
     * Bank 10a7e250 returns the first successful zero-weight child; these four
     * Node conditions do not set weights. Thresholds are strict, never random. */
    public static GreeneWeaponProgram onFoot(double horizontalDistance){
        if(!onFootRange(horizontalDistance))return null;
        if(horizontalDistance>60)return DEATH_FLOWER;
        if(horizontalDistance>40)return RIFLE;
        if(horizontalDistance>25)return SHOTGUN;
        return horizontalDistance>1?INSECT_SWARM:null;
    }
}
