package com.codex.blacklight.client.model;

import com.codex.blacklight.client.renderer.ConsumeAnatomy;

/** Original continuous skinning field, in vanilla model pixels. Shared seam vertices get identical weights. */
public final class BlacklightSkinWeights {
    public record Blend(String first,String second,float secondWeight) { }
    private BlacklightSkinWeights() { }
    private static float ramp(float x,float low,float high) {
        float t=net.minecraft.util.Mth.clamp((x-low)/(high-low),0,1);return t*t*(3-2*t);
    }
    public static Blend sample(int region,float modelY,boolean neck) {
        if(neck)return new Blend("Head","Chest",ramp(modelY,-.5F,1.5F));
        if(region==ConsumeAnatomy.HEAD)return new Blend("Head","Head",0);
        if(region==ConsumeAnatomy.BODY)return modelY<=8
                ?new Blend("Chest","Torso",ramp(modelY,4,8))
                :new Blend("Torso","Root",ramp(modelY,8,12));
        String side=region==ConsumeAnatomy.RIGHT_ARM||region==ConsumeAnatomy.RIGHT_LEG?"R":"L";
        if(region==ConsumeAnatomy.RIGHT_ARM||region==ConsumeAnatomy.LEFT_ARM)return modelY<3
                ?new Blend("Chest","Arm_"+side,ramp(modelY,0,3))
                :new Blend("Arm_"+side,"Hand_"+side,ramp(modelY,5,7));
        return modelY<16
                ?new Blend("Root","Thigh_"+side,ramp(modelY,12,14))
                :new Blend("Thigh_"+side,"Leg_"+side,ramp(modelY,17,19));
    }
}
