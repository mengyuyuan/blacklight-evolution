package com.codex.blacklight.animation;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

/** GreyFrogGames Fly joint curves, CC BY 4.0; see THIRD_PARTY_ASSETS.md. */
public final class BatWingAnimation {
    private static final Map<String,float[][]> CURVES=load();
    private static Map<String,float[][]> load() {
        try(var in=BatWingAnimation.class.getResourceAsStream("/assets/blacklight/animation/sovereign_wingbeat.json")) {
            if(in==null)throw new IllegalStateException("Missing authored wingbeat");
            var samples=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("samples");
            var result=new HashMap<String,float[][]>();
            for(var entry:samples.entrySet()) {
                var array=entry.getValue().getAsJsonArray();float[][] values=new float[array.size()][3];
                if(values.length<2)throw new IllegalStateException("Empty wingbeat curve");
                for(int i=0;i<values.length;i++)for(int axis=0;axis<3;axis++) {
                    values[i][axis]=array.get(i).getAsJsonArray().get(axis).getAsFloat();
                    if(!Float.isFinite(values[i][axis]))throw new IllegalStateException("Invalid wingbeat curve");
                }
                result.put(entry.getKey(),values);
            }
            return Map.copyOf(result);
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot load wingbeat",e);}
    }
    public static float angle(String bone,int axis,double phase) {
        if(!Double.isFinite(phase))phase=0;
        var curve=CURVES.get(bone);double frame=(phase-Math.floor(phase))*(curve.length-1);
        int i=(int)frame;float t=(float)(frame-i);
        return curve[i][axis]+(curve[i+1][axis]-curve[i][axis])*t;
    }
    /** Original arm elevation, centred and normalized; top at 0, bottom at .5. */
    public static float stroke(double phase){return (angle("Arm1.L",0,phase)+11.645F)/45.095F;}
    private BatWingAnimation(){}
}
