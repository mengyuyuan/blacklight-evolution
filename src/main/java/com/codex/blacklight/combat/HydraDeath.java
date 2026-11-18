package com.codex.blacklight.combat;

import com.codex.blacklight.city.HydraMotion;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/** P1 normal death: hyd_death0..98, then hyd_exit from InitFrame27,
 * holding its final frame until the sink node's Death at1.833330s.
 * Each source node is rounded up independently to the MC20Hz clock.
 * Thermobaric1 instead hides the nine body skins at1+1/6s and deletes at1+1s.
 * Its TrackGroup has a separate child clock; it never enters the sink node. */
public final class HydraDeath {
    public static final int NORMAL=0,THERMOBARIC=1;
    public static final int DEATH_LAST_FRAME=98,EXIT_FIRST_FRAME=27,EXIT_LAST_FRAME=54;
    public static final float EXIT_SECONDS=1.8333300352096558F,BLEND_SECONDS=.16666699945926666F;
    public static final int DIE_TICKS=(int)Math.ceil(DEATH_LAST_FRAME/30D*20);
    public static final int SINK_TICKS=(int)Math.ceil(EXIT_SECONDS*20),TOTAL_TICKS=DIE_TICKS+SINK_TICKS;
    public static final float THERMOBARIC_GROUP_SECONDS=1,THERMOBARIC_HIDE_LOCAL_SECONDS=.16666699945926666F,THERMOBARIC_DEATH_LOCAL_SECONDS=1;
    public static final int THERMOBARIC_EFFECT_TICKS=20;
    public static final int THERMOBARIC_HIDE_TICKS=(int)Math.ceil((THERMOBARIC_GROUP_SECONDS+(double)THERMOBARIC_HIDE_LOCAL_SECONDS)*20);
    public static final int THERMOBARIC_TOTAL_TICKS=40;
    private static final float[][][] FRAMES=load();
    private HydraDeath(){}
    private static float[][][] load(){
        try(var in=Objects.requireNonNull(HydraDeath.class.getResourceAsStream("/assets/blacklight/models/hydra_death_motion.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();
            if(!Arrays.equals(HydraMotion.INSTANCE.bones,gson.fromJson(json.get("bones"),String[].class)))throw new IllegalStateException("Hydra death rig mismatch");
            var frames=gson.fromJson(json.get("frames"),float[][][].class);
            if(frames.length!=DEATH_LAST_FRAME+1||json.get("fps").getAsInt()!=30)throw new IllegalStateException("Hydra death source range");
            for(var frame:frames){
                if(frame.length!=HydraMotion.INSTANCE.bones.length)throw new IllegalStateException("Hydra death pose size");
                for(var row:frame){if(row.length!=6)throw new IllegalStateException("Hydra death channel size");for(float v:row)if(!Float.isFinite(v))throw new IllegalStateException("Hydra death nonfinite pose");}
            }
            return frames;
        }catch(Exception e){throw new IllegalStateException("Invalid original Hydra death resource",e);}
    }
    public static boolean sinking(float age){return age>=DIE_TICKS;}
    public static boolean validKind(int kind){return kind==NORMAL||kind==THERMOBARIC;}
    public static int duration(int kind){
        if(!validKind(kind))throw new IllegalArgumentException("Unknown Hydra death kind");
        return kind==THERMOBARIC?THERMOBARIC_TOTAL_TICKS:TOTAL_TICKS;
    }
    public static boolean bodyHidden(int kind,float age){return kind==THERMOBARIC&&age>=THERMOBARIC_HIDE_TICKS;}
    public static float frame(int kind,float age){return kind==THERMOBARIC?Math.min(THERMOBARIC_TOTAL_TICKS,Math.max(0,age))*1.5F:frame(age);}
    public static float[][] pose(int kind,float age){return kind==THERMOBARIC?deathFrame(frame(kind,age)):pose(age);}
    public static float frame(float age){return sinking(age)?Math.min(EXIT_LAST_FRAME,EXIT_FIRST_FRAME+Math.max(0,age-DIE_TICKS)*1.5F):Math.min(DEATH_LAST_FRAME,Math.max(0,age)*1.5F);}
    public static float[][] pose(float age){
        if(!sinking(age))return deathFrame(frame(age));
        float elapsed=Math.max(0,age-DIE_TICKS);
        var next=HydraMotion.INSTANCE.sample(4,frame(age)/1.5F);
        // The outgoing death node and incoming sink both declare1/6s blend.
        // Blend the actual terminal death pose, not the standing idle.
        float weight=Math.min(1,elapsed/(BLEND_SECONDS*20));
        if(weight<1){var before=deathFrame(DEATH_LAST_FRAME);for(int i=0;i<next.length;i++)next[i]=SourceBossMotion.blend(before[i],next[i],weight);}
        return next;
    }
    private static float[][] deathFrame(float frame){
        int lo=(int)frame,hi=Math.min(lo+1,DEATH_LAST_FRAME);var result=new float[HydraMotion.INSTANCE.bones.length][];
        for(int i=0;i<result.length;i++)result[i]=SourceBossMotion.blend(FRAMES[lo][i],FRAMES[hi][i],frame-lo);
        return result;
    }
}
