package com.codex.blacklight.combat;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Sampled rotations, independent of Gecko's local controller clock (including late tracking). */
public final class CreaturePoseTable {
    public static final CreaturePoseTable HUNTER=new CreaturePoseTable();
    public final String[] bones;
    private final Map<String,float[][][]> clips=new HashMap<>();
    private CreaturePoseTable(){
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/assets/blacklight/models/hunter_combat_motion.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();
            bones=gson.fromJson(root.get("bones"),String[].class);
            root.getAsJsonObject("clips").entrySet().forEach(e->clips.put(e.getKey(),gson.fromJson(e.getValue(),float[][][].class)));
        }catch(Exception e){throw new IllegalStateException("Invalid hunter combat motion",e);}
    }
    public float[][] sample(String clip,float ticks){
        var frames=Objects.requireNonNull(clips.get(clip),clip);
        float frame=Math.clamp(ticks*1.5F,0,frames.length-1);int lo=(int)frame,hi=Math.min(lo+1,frames.length-1);
        var result=new float[bones.length][6];
        for(int b=0;b<bones.length;b++)result[b]=blend(frames[lo][b],frames[hi][b],frame-lo);
        return result;
    }
    /** Equivalent Euler representations must not unwind the torso during recovery. */
    public static float[] blend(float[] a,float[] b,float weight){
        var q=new org.joml.Quaternionf().rotationZYX(a[2],a[1],a[0]);
        q.slerp(new org.joml.Quaternionf().rotationZYX(b[2],b[1],b[0]),weight);
        var angles=q.getEulerAnglesZYX(new org.joml.Vector3f());
        var out=new float[]{angles.x,angles.y,angles.z,0,0,0};
        for(int k=3;k<Math.min(a.length,b.length);k++)out[k]=a[k]+(b[k]-a[k])*weight;
        return out;
    }
}
