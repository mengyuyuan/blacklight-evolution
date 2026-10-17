package com.codex.blacklight.city;

import com.google.gson.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Same sampled source poses and articulated grip on the dedicated server and renderer. */
public final class HydraMotion {
    public static final HydraMotion INSTANCE=new HydraMotion();
    public final String[] bones;
    private final int[] parents;
    private final float[][] pivots;
    private final Map<String,float[][][]> clips=new HashMap<>();
    private HydraMotion(){
        try(var in=Objects.requireNonNull(HydraMotion.class.getResourceAsStream("/assets/blacklight/models/hydra_motion.json"));
            var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var gson=new Gson();
            bones=gson.fromJson(root.get("bones"),String[].class);parents=gson.fromJson(root.get("parents"),int[].class);
            pivots=gson.fromJson(root.get("pivots"),float[][].class);
            for(var e:root.getAsJsonObject("clips").entrySet())clips.put(e.getKey(),gson.fromJson(e.getValue(),float[][][].class));
            if(bones.length!=parents.length||bones.length!=pivots.length)throw new IllegalArgumentException("Hydra rig dimensions");
        }catch(Exception e){throw new IllegalStateException("Invalid hydra motion resource",e);}
    }
    public static String clip(int action){return switch(action){case 2->"capture";case 3->"combat_slam";case 4,11->"retract";case 5->"emerge";case 6->"dive";case 7->"arrival";case 8->"toss";case 9->"sweep";case 10->"sidesweep";default->"idle";};}
    public float[][] sample(int action,float ticks){
        var frames=clips.get(clip(action));float frame=Math.max(0,ticks)*1.5F;
        if(action==11)frame=frames.length-1;
        if(action==0||action==1)frame%=frames.length-1;else frame=Math.min(frame,frames.length-1);
        int lo=(int)frame,hi=Math.min(lo+1,frames.length-1);float f=frame-lo;
        var out=new float[bones.length][6];
        for(int b=0;b<bones.length;b++)for(int k=0;k<6;k++)out[b][k]=frames[lo][b][k]+(frames[hi][b][k]-frames[lo][b][k])*f;
        return out;
    }
    public float[][] pose(int action,float ticks,int previous,float previousTicks){
        var next=sample(action,ticks);
        if(ticks<6&&previous>=0){
            var old=sample(previous,previousTicks);float f=net.minecraft.util.Mth.clamp(ticks/6,0,1);f=f*f*(3-2*f);
            for(int b=0;b<bones.length;b++)for(int k=0;k<6;k++){
                float d=next[b][k]-old[b][k];
                if(k<3)d=(float)Math.atan2(Math.sin(d),Math.cos(d));
                next[b][k]=old[b][k]+d*f;
            }
        }
        return next;
    }
    public Matrix4f[] transforms(float[][] pose){
        var result=new Matrix4f[bones.length];
        for(int i=0;i<bones.length;i++){
            var p=pivots[i];var v=pose[i];var m=parents[i]<0?new Matrix4f():new Matrix4f(result[parents[i]]);
            result[i]=m.translate(-v[3],v[4],v[5]).translate(p[0],p[1],p[2]).rotateZYX(v[2],v[1],v[0]).translate(-p[0],-p[1],-p[2]);
        }
        return result;
    }
    public Vector3f grip(float[][] pose){
        var matrices=transforms(pose);
        // Midpoint of matching inner horn surfaces, not an unrelated world-space spiral.
        var left=matrices[Arrays.asList(bones).indexOf("claw_left")].transformPosition(new Vector3f(-24/16F,106/16F,-40/16F));
        var right=matrices[Arrays.asList(bones).indexOf("claw_right")].transformPosition(new Vector3f(24/16F,106/16F,-40/16F));
        return left.add(right).mul(.5F);
    }
}
