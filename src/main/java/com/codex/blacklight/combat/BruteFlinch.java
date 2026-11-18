package com.codex.blacklight.combat;

/** Authored standing recoil for the Brute's heavy arms and short neck. */
public final class BruteFlinch {
    public static boolean standing(int kind){return kind==HitReaction.STAGGER||kind==HitReaction.HEAVY||kind==HitReaction.LASH;}
    public static float ease(float value){float t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
    public static float[][] blend(float[][] from,float[][] to,float weight){
        var result=new float[to.length][];
        for(int i=0;i<result.length;i++)result[i]=CreaturePoseTable.blend(from[i],to[i],weight);
        return result;
    }
    public static float[][] recoil(float[][] idle,int kind,int motion,float progress){
        float pulse=progress<.25F?ease(progress/.25F):1-ease((progress-.25F)/.75F);
        float strong=kind==HitReaction.STAGGER?1:1.4F;
        float side=switch(motion){case ImpactProfile.LEFT->-1;case ImpactProfile.RIGHT,ImpactProfile.BACKHAND,ImpactProfile.CLAW_RIP->1;default->0;};
        var result=new float[idle.length][];
        for(int i=0;i<result.length;i++){
            var p=result[i]=idle[i].clone();float w=pulse*strong;
            switch(CreaturePoseTable.BRUTE.bones[i]){
                case "torso"->{p[0]+=.17F*w;p[1]+=.15F*w*side;p[2]+=.08F*w*side;}
                case "head"->{p[0]-=.19F*w;p[1]-=.12F*w*side;}
                case "right_arm"->{p[0]+=.30F*w;p[2]-=.09F*w;}
                case "left_arm"->{p[0]+=.30F*w;p[2]+=.09F*w;}
                case "right_forearm","left_forearm"->p[0]+=.62F*w;
                default->{}
            }
        }
        return result;
    }
    public static float[][] enter(float[][] previous,float[][] desired,float observedTicks){return blend(previous,desired,ease(observedTicks/2.5F));}
    private BruteFlinch(){}
}
