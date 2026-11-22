package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** The custom spider's original Bedrock clip, shared by the server's hurt regions and the rendered rig. */
public final class SpiderIdle {
    private static final String FILE="/assets/blacklight/animations/entity/greene_spider.animation.json";
    private static final Map<String,Track> TRACKS=new HashMap<>();
    private static final int LENGTH;
    private record Track(float[] times,float[] xyz){
        Quaternionf sample(float tick,float weight){
            int index=Arrays.binarySearch(times,tick);
            int a=index>=0?index:Math.max(0,-index-2);
            int b=Math.min(a+1,times.length-1);
            float u=a==b?0:Mth.clamp((tick-times[a])/(times[b]-times[a]),0,1);
            float x=Mth.lerp(u,xyz[a*3],xyz[b*3])*weight;
            float y=Mth.lerp(u,xyz[a*3+1],xyz[b*3+1])*weight;
            float z=Mth.lerp(u,xyz[a*3+2],xyz[b*3+2])*weight;
            return new Quaternionf().rotateZYX(z,y,x);
        }
    }
    static{
        try(var in=new InputStreamReader(Objects.requireNonNull(SpiderIdle.class.getResourceAsStream(FILE)),StandardCharsets.UTF_8)){
            var clip=JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("animation.greene_spider.idle");
            LENGTH=Math.round(clip.get("animation_length").getAsFloat()*20);
            for(var entry:clip.getAsJsonObject("bones").entrySet()){
                var rotation=entry.getValue().getAsJsonObject().getAsJsonObject("rotation");
                var keys=new java.util.TreeMap<Float,JsonArray>();
                for(var frame:rotation.entrySet())keys.put(Float.parseFloat(frame.getKey())*20,frame.getValue().getAsJsonArray());
                float[] times=new float[keys.size()],xyz=new float[keys.size()*3];int i=0;
                for(var frame:keys.entrySet()){
                    times[i]=frame.getKey();
                    // GeckoLib negates X and Y rotations on Bedrock import.
                    for(int axis=0;axis<3;axis++)xyz[i*3+axis]=(float)Math.toRadians(frame.getValue().get(axis).getAsFloat()*(axis<2?-1:1));
                    i++;
                }
                TRACKS.put(entry.getKey(),new Track(times,xyz));
            }
        }catch(Exception ex){throw new IllegalStateException("Cannot read authored Greene spider idle",ex);}
    }
    private SpiderIdle(){}
    public static float weight(EncounterMob b,float partial){
        boolean rest=b.spider.actions.move()==SpiderActions.Move.REST;
        var state=b.spiderData();
        if(!state.contains("IdleOnEntry"))return rest?1:0;
        return Mth.lerp(SpiderGesture.ease(b.spider.actions.age(partial),0,rest?8:6),
                state.getFloat("IdleOnEntry"),rest?1:0);
    }
    public static float phase(EncounterMob b,float partial){
        return (Math.floorMod(b.level().getGameTime(),LENGTH)+partial)%LENGTH;
    }
    public static Quaternionf mix(EncounterMob b,String joint,float partial,Quaternionf action){
        var track=TRACKS.get(joint);
        if(track==null || !b.spider.live())return action;
        float w=weight(b,partial);
        if(w<=0)return action;
        var idle=track.sample(phase(b,partial),w);
        if(Integer.bitCount(b.spider.brokenMask())>=6 && b.spider.actions.move()==SpiderActions.Move.REST
                && action!=null && (joint.startsWith("elbow_") || joint.startsWith("wrist_")))return idle.mul(action);
        if(action!=null)return w>=1?idle:idle.mul(new Quaternionf().slerp(action,1-w));
        // Persistent mouth damage and an actual meal still move the teeth over the idle clip.
        float flex=b.spider.flex(joint,partial);
        return flex==0?idle:idle.rotateX(flex);
    }
}
