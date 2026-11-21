package com.codex.blacklight.client.model;

import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import org.joml.Vector3f;

/** Local box UVs for the mod's repeating pixel weave. No model UV atlas is required. */
public final class ConsumeAtlasUv {
    private ConsumeAtlasUv() {}
    public static float[] sample(int region,Vector3f position,Vector3f normal,boolean mercer){
        boolean torso=region==ConsumeAnatomy.BODY;
        float armOffset=mercer?.25F:.375F;
        float centerX=torso?0:region==ConsumeAnatomy.RIGHT_ARM?armOffset:-armOffset;
        float width=torso?.53F:mercer?.16F:.27F,depth=torso?.28F:.24F;
        float x=clamp(.5F+(position.x-centerX)/width),z=clamp(.5F+position.z/depth);
        float y=clamp((1.69F-position.y)/.99F);
        float ax=Math.abs(normal.x),ay=Math.abs(normal.y),az=Math.abs(normal.z);
        if(ay>ax&&ay>az)return new float[]{x,normal.y>0?z:1-z};
        // Unwrap each box in one strip, sharing U at adjacent vertical edges.
        float u=az>=ax?(normal.z>=0?x:3-x):(normal.x>=0?2-z:3+z);
        return new float[]{u*.25F,y};
    }
    private static float clamp(float x){return Math.max(0,Math.min(1,x));}
}
