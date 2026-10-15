package com.codex.blacklight.power;

/** No designed tier cap: strength grows with level; flight speed and VFX remain bounded. */
public final class RicochetRules {
    public static final String ID="kinetic_reflection";
    private RicochetRules() {}
    public static int cost(long level){return (int)Math.min(Integer.MAX_VALUE,400D+100D*Math.max(0,level));}
    public static double capacity(long level,boolean shield){return (shield?16D:10D)+(shield?8D:5D)*Math.max(0,level);}
    public static double multiplier(long level){return 1+.12*Math.sqrt(Math.max(0,level));}
    public static double power(double damage,double speed){return damage*(.75+.25*Math.min(32,speed)*Math.min(32,speed));}
    public static float shieldCost(double damage,double units,long level){
        return (float)Math.min(Float.MAX_VALUE/16D,damage*units/(1+.25*Math.max(0,level)));
    }
    public static float strength(double power,double capacity){return (float)Math.max(.15,Math.min(1.8,Math.sqrt(Math.max(0,power)/capacity)));}
}
