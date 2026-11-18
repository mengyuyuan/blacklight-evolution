package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.util.RandomSource;

/** Selected once by the server at confirmation; both participants share the snapshot. */
public final class ConsumeVariant {
    public static final int DEFAULT=0, CLASSIC=1;
    public static final int HEAD_STOMP=2, BEATDOWN=3, KNEE_HEAD=4;
    public static final int STEALTH_NECK=5,STEALTH_BEARHUG=6,STEALTH_NELSON=7,STEALTH_GUILLOTINE=8,STEALTH_JUGULAR=9;
    private ConsumeVariant() {}
    public static boolean alternatives(PowerForm form){return form==PowerForm.NONE||form==PowerForm.CLAWS||form==PowerForm.WHIPFIST;}
    public static int normalize(PowerForm form,int variant){
        if(form==PowerForm.NONE&&variant>=HEAD_STOMP&&variant<=STEALTH_JUGULAR)return variant;
        return (form==PowerForm.CLAWS||form==PowerForm.WHIPFIST)&&variant==CLASSIC?CLASSIC:DEFAULT;
    }
    public static int[] variants(PowerForm form){return form==PowerForm.NONE?new int[]{DEFAULT,HEAD_STOMP,BEATDOWN,KNEE_HEAD,STEALTH_NECK,STEALTH_BEARHUG,STEALTH_NELSON,STEALTH_GUILLOTINE,STEALTH_JUGULAR}:alternatives(form)?new int[]{DEFAULT,CLASSIC}:new int[]{DEFAULT};}
    public static boolean stealth(PowerForm form,int variant){return form==PowerForm.NONE&&variant>=STEALTH_NECK&&variant<=STEALTH_JUGULAR;}
    public static int chooseStealth(RandomSource random){return STEALTH_NECK+random.nextInt(STEALTH_JUGULAR-STEALTH_NECK+1);}
    /** Visual QA only: forces the next choices to one variant; -1 keeps the random pick. */
    public static volatile int qaForced=-1;
    public static int choose(PowerForm form,boolean large,RandomSource random){
        if(qaForced>=0)return large?DEFAULT:normalize(form,qaForced);
        if(large)return DEFAULT;
        int[] choices=form==PowerForm.NONE?new int[]{DEFAULT,HEAD_STOMP,BEATDOWN,KNEE_HEAD}:variants(form);
        return choices[random.nextInt(choices.length)];
    }
}
