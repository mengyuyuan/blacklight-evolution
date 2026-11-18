package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Verified P1 FIG clocks. Source metres and damage are evidence, not MC units. */
public final class HydraCombatAction {
    public record Window(int offset,float from,float to,String joint,float[] sourceOffset,float sourceRadius,float damage,String kind,boolean shapecast) {
        public boolean active(float before,float now){return now>=from&&before<to;}
        public boolean wave(){return offset==30876||offset==31172||offset==31468;}
    }
    public record Turning(float from,float to,float speed) {}
    public final String clip;public final int duration;public final Window[] windows;public final Turning[] turning;
    private static final Map<Integer,HydraCombatAction> ACTIONS=load();
    private HydraCombatAction(){clip="";duration=0;windows=new Window[0];turning=new Turning[0];}
    private static Map<Integer,HydraCombatAction> load(){
        try(var in=Objects.requireNonNull(HydraCombatAction.class.getResourceAsStream("/assets/blacklight/models/hydra_combat.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var result=new HashMap<Integer,HydraCombatAction>();var gson=new Gson();
            for(var e:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("actions").entrySet()){
                var a=gson.fromJson(e.getValue(),HydraCombatAction.class);
                if(a.duration<1||a.windows.length==0)throw new IllegalArgumentException("Empty Hydra action");
                for(var w:a.windows)if(w.from<0||w.to<=w.from||w.to>a.duration||w.damage<=0)throw new IllegalArgumentException("Invalid Hydra window");
                result.put(Integer.parseInt(e.getKey()),a);
            }return Map.copyOf(result);
        }catch(Exception e){throw new IllegalStateException("Invalid Hydra FIG contact table",e);}
    }
    public static HydraCombatAction get(int move){return ACTIONS.get(move);}
    public float turningSpeed(float age){for(var t:turning)if(age>=t.from&&age<t.to)return t.speed/20;return 0;}
}
