package com.codex.blacklight.infection;

/** Bounded food storage; biological age itself keeps advancing without food.
 * One reserve unit is digested per loaded second, never per frame or observer. */
public final class TissueMetabolism {
    public static final int MAX_RESERVE=2400, BONUS_TICKS=180, RECOVERY_DELAY=100;
    private TissueMetabolism() {}
    public static int clamp(int reserve){return Math.clamp(reserve,0,MAX_RESERVE);}
    public static int add(int reserve,int offered){return (int)Math.min(MAX_RESERVE,(long)clamp(reserve)+Math.max(0,offered));}
    /** Use unscaled prey health, with a bounded meal even for edited/modded mobs. */
    public static int meal(float health,TissueCore core){
        if(!Float.isFinite(health)||health<=0)return 0;
        int base=(int)Math.clamp(Math.ceil(health/4D),2,48);
        return core==TissueCore.JAW?(base*5+3)/4:base;
    }
}
