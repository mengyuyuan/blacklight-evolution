package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/** Brute keeps its planted creature rig under a rider; recoil stays at the struck back. */
public final class BruteMountedBody {
    private BruteMountedBody(){}
    public static float[][] pose(ConsumeVisual visual,long gameTime,float partial,int ticks){
        var table=CreaturePoseTable.BRUTE;
        var pose=table.sample("idle",(ticks+partial)%(table.seconds("idle")*20));
        if(!visual.consuming())return pose;
        var forms=PowerForm.values();var form=forms[net.minecraft.util.Mth.clamp(visual.form(),0,forms.length-1)];
        float t=visual.motionProgress(gameTime,partial),at=ConsumeSequence.contact(form,true);
        float recoil=(float)Math.exp(-Math.pow((t-at)*18,2));
        for(int i=0;i<table.bones.length;i++)switch(table.bones[i]){
            case "torso"->{pose[i][0]+=.08F*recoil;pose[i][1]+=(form==PowerForm.CLAWS?.05F:-.05F)*recoil;}
            case "head"->pose[i][0]-=.20F*recoil;
            case "right_arm","left_arm"->pose[i][0]-=.18F*recoil;
        }
        return pose;
    }
}
