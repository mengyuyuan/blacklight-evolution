package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterKind;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Source FIG windows and clip slices. Original values remain inspectable in the resource. */
public final class SourceBossAction {
    public record Segment(float at,float end,float from,float to,float speed,float blendIn,float blendOut) {}
    public record Turning(float begin,float end,float degreesPerSecond,boolean checkDistance,float maxDistance) {}
    public record Window(float begin,float end,float radius,String joint,float[] localOffset,float damage) {}
    private static final Map<String,SourceBossAction> ACTIONS=new HashMap<>();
    static {
        try(var in=Objects.requireNonNull(SourceBossAction.class.getResourceAsStream("/assets/blacklight/models/encounter_source_actions.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var entries=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("actions");
            entries.entrySet().forEach(e->ACTIONS.put(e.getKey(),new SourceBossAction(e.getValue().getAsJsonObject())));
        }catch(Exception ex){throw new IllegalStateException("Invalid source FIG action table",ex);}
    }
    public final String clip,mode,animation,rangeMode;
    public final float seconds,fps,range;
    public final Segment[] segments;
    public final Window[] windows;
    public final Turning[] turning;
    private final EncounterKind spatialModel;
    private SourceBossAction(JsonObject o){
        var gson=new Gson();clip=o.get("clip").getAsString();mode=o.get("mode").getAsString();animation=o.get("animation").getAsString();
        seconds=o.get("seconds").getAsFloat();fps=o.get("fps").getAsFloat();range=o.get("range").getAsFloat();
        segments=gson.fromJson(o.get("segments"),Segment[].class);windows=gson.fromJson(o.get("windows"),Window[].class);
        rangeMode=o.has("rangeMode")?o.get("rangeMode").getAsString():"adapted";
        turning=o.has("turning")?gson.fromJson(o.get("turning"),Turning[].class):new Turning[0];
        String units=o.has("spatialUnits")?o.get("spatialUnits").getAsString():"minecraft";
        if(units.equals("source_model")){
            String model=o.get("spatialModel").getAsString();
            spatialModel=Arrays.stream(EncounterKind.values()).filter(kind->kind.id.equals(model)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown spatial model "+model));
            if(!SourceBossMotion.supports(spatialModel))throw new IllegalArgumentException("No source scale for "+model);
        }else if(units.equals("minecraft"))spatialModel=null;
        else throw new IllegalArgumentException("Unknown action spatial units "+units);
    }
    public static SourceBossAction get(EncounterKind kind,EncounterMove move){return get(kind,move.name());}
    public static SourceBossAction decode(JsonObject object){return new SourceBossAction(object);}
    public static SourceBossAction get(EncounterKind kind,String state){return ACTIONS.get(kind.id+":"+state);}
    public int duration(){return (int)Math.ceil(seconds*20);}
    public int contact(){return Arrays.stream(windows).mapToInt(w->(int)Math.ceil(w.begin*20-0.001F)).min().orElse(duration());}
    public boolean impact(int age){
        for(var w:windows){int begin=(int)Math.ceil(w.begin*20-.001F),end=(int)Math.floor(w.end*20+.001F);
            if(age>=begin&&age<=Math.max(begin,end))return true;
        }return false;
    }
    /** Original stored units, retained for source audits; not a world distance. */
    public float radius(int age){float r=0;for(var w:windows)if(age>=Math.ceil(w.begin*20-.001F))r=Math.max(r,w.radius);return r;}
    /** Only explicitly source-unit actions use the associated preserved model's
     * pixels/16 scale. Existing Minecraft-authored ranges remain unchanged. */
    public double spatialScale(){return spatialModel==null?1:SourceBossMotion.get(spatialModel).sourceUnitBlocks();}
    public double worldRange(){return range*spatialScale();}
    public double worldRadius(int age){return radius(age)*spatialScale();}
    public double worldRadius(Window window){return window.radius()*spatialScale();}
    public float waveDamageScale(int age,double worldDistance){
        float largest=0;for(var w:windows)largest=Math.max(largest,w.damage);
        for(var w:windows)if(age>=Math.ceil(w.begin*20-.001F)&&age<=Math.floor(w.end*20+.001F)&&worldDistance<=worldRadius(w))return largest>0?w.damage/largest:1;
        return 0;
    }
    public float turnStep(int age,double distance){for(var t:turning)if(age/20F>=t.begin&&age/20F<=t.end&&(!t.checkDistance||distance<=t.maxDistance))return t.degreesPerSecond/20;return 0;}
    public float sourceTick(float age){
        float seconds=Math.max(0,age/20);Segment chosen=segments[0];
        for(var s:segments)if(seconds>=s.at)chosen=s;
        float frame=chosen.from+Math.max(0,seconds-chosen.at)*fps*chosen.speed;
        if(chosen.to>=0)frame=Math.min(frame,chosen.to);
        return frame/fps*20;
    }
}
