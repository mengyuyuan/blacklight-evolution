package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.util.RandomSource;

/** Selected once by the server at confirmation; both participants share the snapshot. */
public final class ConsumeVariant {
    public static final int DEFAULT=0, CLASSIC=1;
    private ConsumeVariant() {}
    public static boolean alternatives(PowerForm form){return form==PowerForm.CLAWS||form==PowerForm.WHIPFIST;}
    public static int normalize(PowerForm form,int variant){return alternatives(form)&&variant==CLASSIC?CLASSIC:DEFAULT;}
    /** Visual QA only: forces the next choices to one variant; -1 keeps the random pick. */
    public static volatile int qaForced=-1;
    public static int choose(PowerForm form,boolean large,RandomSource random){
        if(qaForced>=0)return large?DEFAULT:normalize(form,qaForced);
        return !large&&alternatives(form)&&random.nextBoolean()?CLASSIC:DEFAULT;
    }
}
