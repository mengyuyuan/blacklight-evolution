package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;

/** alex_fig ordinary receivers call giver/cyclic -> AlexBody, branch 110. */
public final class ConsumeGiverEffect {
    public static final float REVERSE_SECONDS=69F/30, END_SECONDS=200F/30;
    private ConsumeGiverEffect() {}

    public static float cue(PowerForm form,int variant){
        if(form!=PowerForm.NONE&&form!=PowerForm.CLAWS&&form!=PowerForm.HAMMERFISTS
                &&form!=PowerForm.MUSCLE_MASS&&form!=PowerForm.WHIPFIST&&form!=PowerForm.BLADE)return Float.NaN;
        int frame=switch(ConsumeSequence.key(form,variant)){
            case "normal" -> 55;
            case "normal_head_stomp" -> 35;
            case "normal_beatdown" -> 88;
            case "normal_knee_head" -> 45;
            case "claws","muscle_heart" -> 40;
            case "claws_head" -> 130;
            case "hammer" -> 86;
            case "hammer_smush" -> 77;
            case "muscle" -> 36;
            case "whip" -> 50;
            case "whip_lash" -> 60;
            case "blade" -> 25;
            default -> -1;
        };
        return frame<0?Float.NaN:frame/30F;
    }
    public static float cue(ConsumeVisual scene){
        var forms=PowerForm.values();
        return scene.form()>=0&&scene.form()<forms.length?cue(forms[scene.form()],scene.variant()):Float.NaN;
    }
    public static int tick(float seconds){return (int)Math.ceil(seconds*20-.0001F);}
    public static float reach(float age){
        if(!(age>0)||age>=END_SECONDS)return 0;
        return .35F*Math.max(0,Math.min(age,2*REVERSE_SECONDS-age));
    }
}
