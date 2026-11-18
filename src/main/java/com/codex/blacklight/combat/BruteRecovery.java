package com.codex.blacklight.combat;

/** Ground contact holds the weight, clears the fists, then stands without repeating the windup. */
public final class BruteRecovery {
    public static final int HOLD=3,LIFT=8,FOLD=11,RETRACT=14,LOWER=21,TICKS=25;
    public static float[][] pose(float contactAge,float elapsed){
        var table=CreaturePoseTable.BRUTE;
        var contact=table.sample(MutantMove.BRUTE_SLAM.clip,contactAge);
        if(elapsed<=HOLD)return contact;
        var lifted=table.sample(MutantMove.BRUTE_SLAM.clip,contactAge);
        for(int i=0;i<table.bones.length;i++){
            String bone=table.bones[i];
            if(bone.equals("torso")){lifted[i][0]*=.85F;lifted[i][4]+=.75F;}
            else if(bone.endsWith("_forearm")){
                int upper=java.util.Arrays.asList(table.bones).indexOf(bone.replace("_forearm","_arm"));
                int torso=java.util.Arrays.asList(table.bones).indexOf("torso");
                lifted[i][0]=Math.max(0,(float)Math.toRadians(130)-lifted[torso][0]-lifted[upper][0]);
            }
        }
        if(elapsed<LIFT)return blend(contact,lifted,ease((elapsed-HOLD)/(LIFT-HOLD)));
        // Fold at the elbows and pull beside the hips before lowering. A rigid
        // shoulder-only descent would cut through the raised impact surface.
        var folded=table.sample("idle",0);var retracted=table.sample("idle",0);var lowered=table.sample("idle",0);
        for(int i=0;i<table.bones.length;i++){
            String bone=table.bones[i];
            if(bone.endsWith("_arm")){folded[i][0]=(float)Math.toRadians(10);retracted[i][0]=(float)Math.toRadians(-45);lowered[i][0]=(float)Math.toRadians(-30);}
            else if(bone.endsWith("_forearm")){folded[i][0]=(float)Math.toRadians(160);retracted[i][0]=(float)Math.toRadians(175);lowered[i][0]=(float)Math.toRadians(40);}
        }
        if(elapsed<FOLD)return blend(lifted,folded,ease((elapsed-LIFT)/(FOLD-LIFT)));
        if(elapsed<RETRACT)return blend(folded,retracted,ease((elapsed-FOLD)/(RETRACT-FOLD)));
        if(elapsed<LOWER)return blend(retracted,lowered,ease((elapsed-RETRACT)/(LOWER-RETRACT)));
        return blend(lowered,table.sample("idle",0),ease((elapsed-LOWER)/(TICKS-LOWER)));
    }
    private static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static float[][] blend(float[][] from,float[][] to,float weight){
        var result=new float[from.length][];
        for(int i=0;i<from.length;i++)result[i]=CreaturePoseTable.blend(from[i],to[i],weight);
        return result;
    }
    private BruteRecovery(){}
}
