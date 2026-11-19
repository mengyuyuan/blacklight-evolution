package com.codex.blacklight.client.animation;

import com.codex.blacklight.power.PowerForm;

/** Original transformation-system start markers, in ticks of the existing body clip. */
public final class WeaponMorphTiming {
    public static final float QUICK_TICKS=16, RETRACT_TICKS=14, SWITCH_TICKS=18;
    private WeaponMorphTiming() { }
    public static float onset(PowerForm form) {
        return switch(form) {
            case CLAWS -> 50;
            case BLADE -> 39.33334F;
            case HAMMERFISTS -> 56.66666F;
            case MUSCLE_MASS,WHIPFIST -> 48.66666F;
            default -> 0;
        };
    }
    public static boolean weapon(PowerForm form) {
        return form==PowerForm.CLAWS||form==PowerForm.BLADE||form==PowerForm.HAMMERFISTS
                ||form==PowerForm.MUSCLE_MASS||form==PowerForm.WHIPFIST;
    }
    public static boolean hasMorph(PowerForm form) {
        return weapon(form)||form==PowerForm.BACKSPINES;
    }
    public static boolean arm(PowerForm form,boolean left) {
        return weapon(form)&&(!left||form==PowerForm.CLAWS||form==PowerForm.HAMMERFISTS||form==PowerForm.MUSCLE_MASS);
    }
}
