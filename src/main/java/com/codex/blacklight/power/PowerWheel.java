package com.codex.blacklight.power;

/** Shared wheel order and dead-zone geometry; selection never purchases a power. */
public final class PowerWheel {
    private static final PowerForm[] FORMS={PowerForm.CLAWS,PowerForm.BLADE,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS,PowerForm.NONE};
    private static final PowerForm[] UTILITIES={PowerForm.SHIELD,PowerForm.ARMOR,PowerForm.THERMAL_VISION,PowerForm.INFECTED_VISION,PowerForm.SOVEREIGN_WINGS};
    private PowerWheel() {}
    public static int size(){return FORMS.length;}
    public static PowerForm form(int index){return FORMS[index];}
    public static int size(boolean utility){return utility?UTILITIES.length:FORMS.length;}
    public static PowerForm form(boolean utility,int index){return utility?UTILITIES[index]:FORMS[index];}
    public static int sector(double x,double y,double inner,double outer) {
        return sector(x,y,inner,outer,false);
    }
    public static int sector(double x,double y,double inner,double outer,boolean utility) {
        int count=size(utility);
        double radius=Math.hypot(x,y);
        if(radius<inner||radius>outer||!Double.isFinite(radius))return -1;
        double angle=Math.atan2(y,x)+Math.PI/2+Math.PI/count;
        angle=(angle%(Math.PI*2)+Math.PI*2)%(Math.PI*2);
        return Math.min(count-1,(int)(angle/(Math.PI*2/count)));
    }
}
