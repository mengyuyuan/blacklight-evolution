package com.codex.blacklight.combat;

import java.util.*;

/** Private SCL channels, sampled on the same retargeted clip clock as body animation. */
public final class HammerScales {
    public record Shape(float forearm,float fist) {
        public static final Shape IDENTITY=new Shape(1,1);
        public Shape blend(Shape other,float weight) {
            return new Shape(forearm+(other.forearm-forearm)*weight,fist+(other.fist-fist)*weight);
        }
    }
    private record Clip(float[] times,float[][] frames) {}
    private static Map<String,Clip> clips;
    private HammerScales() {}
    private static synchronized Map<String,Clip> data() {
        if(clips!=null)return clips;
        var resource=MotionResources.read("hammer-scales.data",1024*1024);
        if(resource.isEmpty())return clips=Map.of();
        var root=resource.orElseThrow();
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Hammer scale schema");
        var names=root.getAsJsonArray("bones");String[] expected={"Forarm_R","Wrist_R","Forarm_L","Wrist_L"};
        if(names.size()!=expected.length)throw new IllegalArgumentException("Hammer scale bones");
        for(int i=0;i<expected.length;i++)if(!expected[i].equals(names.get(i).getAsString()))throw new IllegalArgumentException("Hammer scale order");
        Map<String,Clip> result=new HashMap<>();
        for(var entry:root.getAsJsonObject("clips").entrySet()) {
            var obj=entry.getValue().getAsJsonObject();var ts=obj.getAsJsonArray("times");var rows=obj.getAsJsonArray("frames");
            if(ts.size()<2||ts.size()>4000||ts.size()!=rows.size())throw new IllegalArgumentException("Hammer scale count");
            float[] times=new float[ts.size()];float[][] frames=new float[ts.size()][4];
            for(int i=0;i<times.length;i++) {
                times[i]=ts.get(i).getAsFloat();
                if(!Float.isFinite(times[i])||times[i]<0||times[i]>30||(i>0&&times[i]<=times[i-1]))throw new IllegalArgumentException("Hammer scale time");
                var row=rows.get(i).getAsJsonArray();if(row.size()!=4)throw new IllegalArgumentException("Hammer scale width");
                for(int j=0;j<4;j++) {
                    frames[i][j]=row.get(j).getAsFloat();
                    if(!Float.isFinite(frames[i][j])||frames[i][j]<.1F||frames[i][j]>5)throw new IllegalArgumentException("Hammer scale value");
                }
            }
            result.put(entry.getKey(),new Clip(times,frames));
        }
        return clips=Map.copyOf(result);
    }
    public static boolean contains(String key) { return data().containsKey(key); }
    public static Shape sample(String key,float time,boolean left) {
        var clip=data().get(key);if(clip==null||!Float.isFinite(time))return Shape.IDENTITY;
        int found=Arrays.binarySearch(clip.times,time);
        int a=found>=0?found:Math.max(0,-found-2),b=Math.min(a+1,clip.times.length-1);
        float weight=a==b?0:com.codex.blacklight.forge.WingMath.clamp((time-clip.times[a])/(clip.times[b]-clip.times[a]),0,1);
        int offset=left?2:0;
        return new Shape(clip.frames[a][offset],clip.frames[a][offset+1])
                .blend(new Shape(clip.frames[b][offset],clip.frames[b][offset+1]),weight);
    }
}
