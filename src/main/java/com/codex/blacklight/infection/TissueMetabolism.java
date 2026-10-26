package com.codex.blacklight.infection;

/** Food pays for actual growth/repair, never idle decay. Natural age still advances unfed. */
public final class TissueMetabolism {
    public static final int MAX_RESERVE=2400, BONUS_TICKS=180, RECOVERY_DELAY=100;
    public static final int WORK_PER_UNIT=100;
    public record Budget(int reserve,int debt,boolean supplied,double fraction) {
        public Budget(int reserve,int debt,boolean supplied){this(reserve,debt,supplied,supplied?1:0);}
    }
    private TissueMetabolism() {}
    public static int clamp(int reserve){return Math.clamp(reserve,0,MAX_RESERVE);}
    public static int add(int reserve,int offered){return (int)Math.min(MAX_RESERVE,(long)clamp(reserve)+Math.max(0,offered));}
    public static int initialReserve(float originalHealth){return !Float.isFinite(originalHealth)||originalHealth<=0?0:Math.clamp(meal(originalHealth,TissueCore.MUSCLE)*2,8,24);}
    public static boolean growing(long age,boolean integrating){return age<TissueGrowth.ANCIENT_TICKS||integrating;}
    public static long growthPhase(long age){return TissueGrowth.generation(age);}
    public static double phaseFood(long age){return 4+3D*growthPhase(Math.max(0,age));}
    public static double worldSpeed(long overworldDayTime){return 1+Math.sqrt((Math.max(0,overworldDayTime)/24000L)/7D);}
    public static long scaledTicks(long ticks,double speed){return ticks<=0?0:!Double.isFinite(speed)||speed<=0?ticks:(long)Math.min(Long.MAX_VALUE,Math.max(1,ticks*speed));}
    /** Estimated extra food per biological stage; natural age has no food fee. */
    public static int demand(long age,boolean integrating,boolean repairing){
        return demand(age,integrating,repairing,1);
    }
    public static int demand(long age,boolean integrating,boolean repairing,double speed){
        int phaseLength=age<24000?12000:24000;
        double growth=growing(age,integrating)?phaseFood(age)*BONUS_TICKS*speed/phaseLength*WORK_PER_UNIT:0;
        return (int)Math.min(Integer.MAX_VALUE,Math.ceil(growth+(repairing?20:0)));
    }
    public static Budget budget(int reserve,int debt,int demand){
        reserve=clamp(reserve);debt=Math.clamp(debt,0,WORK_PER_UNIT-1);
        if(reserve==0||demand<=0)return new Budget(reserve,debt,false);
        long available=(long)reserve*WORK_PER_UNIT-debt,paid=Math.min(available,(long)demand),work=debt+paid;
        return new Budget(reserve-(int)(work/WORK_PER_UNIT),(int)(work%WORK_PER_UNIT),paid>0,paid/(double)demand);
    }
    /** Use unscaled prey health, with a bounded meal even for edited/modded mobs. */
    public static int meal(float health,TissueCore core){
        if(!Float.isFinite(health)||health<=0)return 0;
        int base=(int)Math.clamp(Math.ceil(health/4D),2,48);
        return core==TissueCore.JAW?(base*5+3)/4:base;
    }
}
